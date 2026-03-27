package com.studentassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.OffsetDateTime;
import java.util.Map;

public record HabitEventIn(
        @NotBlank String assignmentId,
        @Pattern(regexp = "opened|worked|submitted|snoozed") String event,
        Long courseId,
        OffsetDateTime dueAt,
        Map<String, Object> metadata
) {
    public HabitEventIn {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
