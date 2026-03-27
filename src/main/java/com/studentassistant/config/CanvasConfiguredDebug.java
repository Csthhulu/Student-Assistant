package com.studentassistant.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class CanvasConfiguredDebug {

    private final AppProperties appProperties;

    public CanvasConfiguredDebug(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    // #region agent log
    @PostConstruct
    public void logCanvasConfiguredSnapshot() {
        try {
            String envUrl = System.getenv("CANVAS_BASE_URL");
            String envTok = System.getenv("CANVAS_ACCESS_TOKEN");
            String envUrl2 = System.getenv("STUDENT_ASSISTANT_CANVAS_BASE_URL");
            String envTok2 = System.getenv("STUDENT_ASSISTANT_CANVAS_ACCESS_TOKEN");

            String propUrl = appProperties.canvasBaseUrl();
            String propTok = appProperties.canvasAccessToken();
            boolean configured = appProperties.canvasConfigured();

            // IMPORTANT: never log secrets; only lengths/booleans.
            String line = "{\"sessionId\":\"fb4f75\",\"runId\":\"pre-fix\",\"hypothesisId\":\"H1-H5\","
                    + "\"location\":\"CanvasConfiguredDebug.postConstruct\","
                    + "\"message\":\"canvas_configured snapshot\","
                    + "\"timestamp\":" + System.currentTimeMillis() + ","
                    + "\"data\":{"
                    + "\"env_CANVAS_BASE_URL_defined\":" + (envUrl != null) + ","
                    + "\"env_CANVAS_BASE_URL_len\":" + (envUrl != null ? envUrl.length() : 0) + ","
                    + "\"env_CANVAS_ACCESS_TOKEN_defined\":" + (envTok != null) + ","
                    + "\"env_CANVAS_ACCESS_TOKEN_len\":" + (envTok != null ? envTok.length() : 0) + ","
                    + "\"env_STUDENT_ASSISTANT_CANVAS_BASE_URL_defined\":" + (envUrl2 != null) + ","
                    + "\"env_STUDENT_ASSISTANT_CANVAS_BASE_URL_len\":" + (envUrl2 != null ? envUrl2.length() : 0) + ","
                    + "\"env_STUDENT_ASSISTANT_CANVAS_ACCESS_TOKEN_defined\":" + (envTok2 != null) + ","
                    + "\"env_STUDENT_ASSISTANT_CANVAS_ACCESS_TOKEN_len\":" + (envTok2 != null ? envTok2.length() : 0) + ","
                    + "\"prop_canvasBaseUrl_len\":" + (propUrl != null ? propUrl.length() : -1) + ","
                    + "\"prop_canvasBaseUrl_blank\":" + (propUrl == null || propUrl.isBlank()) + ","
                    + "\"prop_canvasAccessToken_len\":" + (propTok != null ? propTok.length() : -1) + ","
                    + "\"prop_canvasAccessToken_blank\":" + (propTok == null || propTok.isBlank()) + ","
                    + "\"canvas_configured\":" + configured
                    + "}"
                    + "}\n";

            Path logPath = Path.of(System.getProperty("user.home"), "debug-fb4f75.log");
            Files.writeString(
                    logPath,
                    line,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Throwable ignored) {
            // debug only
        }
    }
    // #endregion
}

