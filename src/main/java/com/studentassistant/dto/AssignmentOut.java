package com.studentassistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssignmentOut(
        String id,
        Long canvasAssignmentId,
        Long courseId,
        String courseName,
        String name,
        OffsetDateTime dueAt,
        Double pointsPossible,
        String htmlUrl,
        java.util.List<String> submissionTypes,
        Boolean hasSubmitted,
        double habitScore
) {
}
