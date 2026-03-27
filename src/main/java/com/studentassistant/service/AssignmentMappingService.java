package com.studentassistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentassistant.canvas.CanvasParsing;
import com.studentassistant.dto.AssignmentOut;
import com.studentassistant.habits.HabitService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AssignmentMappingService {

    private final HabitService habitService;

    public AssignmentMappingService(HabitService habitService) {
        this.habitService = habitService;
    }

    public List<AssignmentOut> fromCanvasNodes(List<JsonNode> rawList) {
        List<AssignmentOut> out = new ArrayList<>();
        for (JsonNode raw : rawList) {
            out.add(fromCanvasNode(raw));
        }
        return out;
    }

    public AssignmentOut fromCanvasNode(JsonNode raw) {
        Long courseId = raw.has("_course_id") ? raw.get("_course_id").asLong() : null;
        var due = CanvasParsing.parseDueAt(raw.get("due_at"));
        Long aid = raw.has("id") ? raw.get("id").asLong() : null;
        String assignmentIdStr = (courseId != null && aid != null)
                ? courseId + "-" + aid
                : String.valueOf(aid);
        double score = habitService.habitUrgencyScore(courseId, due);
        Boolean submitted = null;
        if (raw.has("has_submitted_submissions") && raw.get("has_submitted_submissions").isBoolean()) {
            submitted = raw.get("has_submitted_submissions").asBoolean();
        }
        List<String> subTypes = null;
        if (raw.has("submission_types") && raw.get("submission_types").isArray()) {
            subTypes = new ArrayList<>();
            for (JsonNode n : raw.get("submission_types")) {
                subTypes.add(n.asText());
            }
        }
        String name = raw.has("name") ? raw.get("name").asText("Assignment") : "Assignment";
        Double points = raw.has("points_possible") && !raw.get("points_possible").isNull()
                ? raw.get("points_possible").asDouble()
                : null;
        String htmlUrl = raw.has("html_url") ? raw.get("html_url").asText(null) : null;
        return new AssignmentOut(
                assignmentIdStr,
                aid,
                courseId,
                raw.has("_course_name") ? raw.get("_course_name").asText(null) : null,
                name,
                due,
                points,
                htmlUrl,
                subTypes,
                submitted,
                score
        );
    }
}
