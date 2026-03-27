package com.studentassistant.coach;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studentassistant.config.AppProperties;
import com.studentassistant.dto.AssignmentOut;
import com.studentassistant.dto.ChatMessage;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AssistantService {

    private static final DateTimeFormatter BRIEF_DF =
            DateTimeFormatter.ofPattern("EEE MMM dd HH:mm", Locale.US).withZone(ZoneOffset.UTC);

    private final AppProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public AssistantService(AppProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public String heuristicBrief(List<AssignmentOut> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return "No upcoming assignments from Canvas right now. Good time to review notes or get ahead.";
        }
        Comparator<AssignmentOut> cmp = Comparator
                .comparingDouble(AssignmentOut::habitScore).reversed()
                .thenComparing(a -> a.dueAt() != null ? a.dueAt() : OffsetDateTime.MAX);
        List<AssignmentOut> top = assignments.stream().sorted(cmp).limit(5).collect(Collectors.toList());
        StringBuilder sb = new StringBuilder("Top priorities (habit-adjusted):\n");
        for (AssignmentOut a : top) {
            String due = a.dueAt() != null ? BRIEF_DF.format(a.dueAt().toInstant()) : "no due date";
            String course = a.courseName() != null ? a.courseName() : "Course";
            sb.append(String.format(Locale.US, "- %s: %s (due %s; focus score %.2f)%n", course, a.name(), due, a.habitScore()));
        }
        sb.append("Tip: Log when you work or submit so the assistant learns when you usually finish.");
        return sb.toString().trim();
    }

    public String buildCanvasContext(List<AssignmentOut> assignments, Map<String, Object> habitSummary) throws Exception {
        String now = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'"));
        StringBuilder lines = new StringBuilder("Now: ").append(now).append("\n\nAssignments:\n");
        OffsetDateTime far = OffsetDateTime.of(2099, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        List<AssignmentOut> sorted = assignments.stream()
                .sorted(Comparator.comparing(a -> a.dueAt() != null ? a.dueAt() : far))
                .collect(Collectors.toList());
        for (AssignmentOut a : sorted) {
            String due = a.dueAt() != null ? a.dueAt().toString() : "none";
            lines.append(String.format(Locale.US,
                    "- [%s] %s | due=%s | points=%s | habit_score=%.2f%n",
                    a.courseName(),
                    a.name(),
                    due,
                    a.pointsPossible(),
                    a.habitScore()));
        }
        lines.append("\nHabit summary JSON: ").append(objectMapper.writeValueAsString(habitSummary));
        return lines.toString();
    }

    public String llmReply(List<ChatMessage> messages, String systemAddendum) throws Exception {
        if (!props.geminiConfigured()) {
            throw new IllegalStateException("GEMINI_API_KEY not set");
        }
        StringBuilder systemText = new StringBuilder(
                "You are a concise student success coach. Help prioritize, plan short sessions, "
                        + "and reduce overwhelm. Prefer actionable steps. If assignment data is provided, "
                        + "ground advice in it. Never fabricate due dates.");
        if (systemAddendum != null && !systemAddendum.isBlank()) {
            systemText.append("\n\nContext:\n").append(systemAddendum);
        }

        ArrayNode contents = objectMapper.createArrayNode();
        for (ChatMessage m : messages) {
            String role = m.role() != null ? m.role().toLowerCase(Locale.ROOT) : "user";
            if ("system".equals(role)) {
                systemText.append("\n\n").append(m.content());
                continue;
            }
            boolean isModel = "assistant".equals(role) || "model".equals(role);
            ObjectNode entry = contents.addObject();
            entry.put("role", isModel ? "model" : "user");
            ArrayNode parts = entry.putArray("parts");
            parts.addObject().put("text", m.content() != null ? m.content() : "");
        }

        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode systemInstruction = root.putObject("systemInstruction");
        ArrayNode sysParts = systemInstruction.putArray("parts");
        sysParts.addObject().put("text", systemText.toString());
        root.set("contents", contents);

        ObjectNode gen = root.putObject("generationConfig");
        gen.put("temperature", 0.5);
        gen.put("maxOutputTokens", 4096);

        String modelId = props.geminiModel() != null && !props.geminiModel().isBlank()
                ? props.geminiModel().trim()
                : "gemini-2.0-flash";
        if (modelId.startsWith("models/")) {
            modelId = modelId.substring("models/".length());
        }

        String keyQ = URLEncoder.encode(props.geminiApiKey(), StandardCharsets.UTF_8);
        String uri = "https://generativelanguage.googleapis.com/v1beta/models/"
                + modelId
                + ":generateContent?key="
                + keyQ;

        String body = objectMapper.writeValueAsString(root);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("Gemini error HTTP " + resp.statusCode() + ": " + resp.body());
        }
        JsonNode tree = objectMapper.readTree(resp.body());
        JsonNode err = tree.path("error");
        if (!err.isMissingNode() && err.path("message") != null && !err.path("message").asText("").isEmpty()) {
            throw new IllegalStateException("Gemini API: " + err.path("message").asText());
        }
        JsonNode candidates = tree.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            String reason = tree.path("promptFeedback").path("blockReason").asText("");
            throw new IllegalStateException("Gemini returned no response"
                    + (reason.isEmpty() ? "" : (" (" + reason + ")")));
        }
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray()) {
            return "";
        }
        StringBuilder textOut = new StringBuilder();
        for (JsonNode p : parts) {
            if (p.has("text")) {
                textOut.append(p.get("text").asText());
            }
        }
        return textOut.toString().trim();
    }
}
