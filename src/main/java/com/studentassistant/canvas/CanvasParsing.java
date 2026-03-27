package com.studentassistant.canvas;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public final class CanvasParsing {

    private CanvasParsing() {
    }

    public static OffsetDateTime parseDueAt(JsonNode raw) {
        if (raw == null || raw.isNull()) {
            return null;
        }
        String s = raw.asText(null);
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(s.replace("Z", "+00:00"));
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
