package com.studentassistant.dto;

import java.util.List;
import java.util.Map;

public record DashboardOut(
        List<AssignmentOut> assignments,
        Map<String, Object> habitSummary,
        String coachMessage
) {
}
