package com.studentassistant.web;

import com.studentassistant.canvas.CanvasClient;
import com.studentassistant.coach.AssistantService;
import com.studentassistant.config.AppProperties;
import com.studentassistant.dto.AssignmentOut;
import com.studentassistant.dto.ChatIn;
import com.studentassistant.dto.ChatMessage;
import com.studentassistant.dto.ChatOut;
import com.studentassistant.dto.DashboardOut;
import com.studentassistant.dto.HabitEventIn;
import com.studentassistant.habits.HabitService;
import com.studentassistant.service.AssignmentMappingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
public class ApiController {

    private final AppProperties appProperties;
    private final CanvasClient canvasClient;
    private final HabitService habitService;
    private final AssistantService assistantService;
    private final AssignmentMappingService assignmentMappingService;

    public ApiController(
            AppProperties appProperties,
            CanvasClient canvasClient,
            HabitService habitService,
            AssistantService assistantService,
            AssignmentMappingService assignmentMappingService
    ) {
        this.appProperties = appProperties;
        this.canvasClient = canvasClient;
        this.habitService = habitService;
        this.assistantService = assistantService;
        this.assignmentMappingService = assignmentMappingService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "canvas_configured", appProperties.canvasConfigured());
    }

    @GetMapping("/assignments")
    public List<AssignmentOut> assignments() {
        var raw = canvasClient.fetchAllUpcomingAssignments();
        var list = assignmentMappingService.fromCanvasNodes(raw);
        var far = OffsetDateTime.of(9999, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);
        list.sort(Comparator.comparing(a -> a.dueAt() != null ? a.dueAt() : far));
        return list;
    }

    @PostMapping("/habits/event")
    public Map<String, Boolean> habitEvent(@Valid @RequestBody HabitEventIn body) {
        habitService.recordEvent(
                body.assignmentId(),
                body.event(),
                body.courseId(),
                body.metadata(),
                body.dueAt()
        );
        return Map.of("ok", true);
    }

    @GetMapping("/dashboard")
    public DashboardOut dashboard() throws Exception {
        var raw = canvasClient.fetchAllUpcomingAssignments();
        var assignments = assignmentMappingService.fromCanvasNodes(raw);
        var hsum = habitService.summary();
        String coach = assistantService.heuristicBrief(assignments);
        if (appProperties.geminiConfigured()) {
            try {
                var ctx = assistantService.buildCanvasContext(assignments, hsum);
                coach = assistantService.llmReply(
                        List.of(new ChatMessage("user", "Give a short daily plan (max 6 bullets).")),
                        ctx
                );
            } catch (Exception ignored) {
                // keep heuristic brief
            }
        }
        return new DashboardOut(assignments, hsum, coach);
    }

    @PostMapping("/chat")
    public ChatOut chat(@RequestBody ChatIn body) throws Exception {
        if (body.messages() == null || body.messages().isEmpty()) {
            return new ChatOut("Send at least one message.", false);
        }
        String canvasCtx = null;
        if (Boolean.TRUE.equals(body.includeCanvas()) && appProperties.canvasConfigured()) {
            try {
                var raw = canvasClient.fetchAllUpcomingAssignments();
                var assignments = assignmentMappingService.fromCanvasNodes(raw);
                canvasCtx = assistantService.buildCanvasContext(assignments, habitService.summary());
            } catch (Exception e) {
                canvasCtx = "Canvas unavailable; answer generally.";
            }
        }
        if (appProperties.geminiConfigured()) {
            String reply = assistantService.llmReply(body.messages(), canvasCtx);
            return new ChatOut(reply, true);
        }
        var msgs = body.messages();
        String last = msgs != null && !msgs.isEmpty()
                ? msgs.get(msgs.size() - 1).content()
                : "";
        return new ChatOut(
                "Configure GEMINI_API_KEY for full chat. Quick note: prioritize by nearest due dates "
                        + "and log work in /habits/event. You said: \"" + last + "\"",
                false
        );
    }
}
