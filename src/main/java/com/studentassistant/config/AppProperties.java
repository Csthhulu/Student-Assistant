package com.studentassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "student-assistant")
public record AppProperties(
        String canvasBaseUrl,
        String canvasAccessToken,
        String geminiApiKey,
        String geminiModel
) {
    public boolean canvasConfigured() {
        return canvasBaseUrl != null && !canvasBaseUrl.isBlank()
                && canvasAccessToken != null && !canvasAccessToken.isBlank();
    }

    public boolean geminiConfigured() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }
}
