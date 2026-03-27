package com.studentassistant.dto;

import java.util.List;

public record ChatIn(List<ChatMessage> messages, Boolean includeCanvas) {

    public ChatIn {
        if (includeCanvas == null) {
            includeCanvas = true;
        }
    }
}
