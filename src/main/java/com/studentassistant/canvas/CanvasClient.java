package com.studentassistant.canvas;

import com.fasterxml.jackson.databind.JsonNode;
import com.studentassistant.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class CanvasClient {

    private final AppProperties props;
    private final RestClient.Builder restClientBuilder;

    public CanvasClient(AppProperties props, RestClient.Builder restClientBuilder) {
        this.props = props;
        this.restClientBuilder = restClientBuilder;
    }

    public List<JsonNode> fetchAllUpcomingAssignments() {
        String base = canvasBase();
        String token = canvasToken();
        RestClient client = restClientBuilder
                .baseUrl(base)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/json")
                .build();

        JsonNode courses = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/courses")
                        .queryParam("enrollment_state", "active")
                        .queryParam("per_page", 100)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        if (courses == null || !courses.isArray()) {
            return List.of();
        }

        List<JsonNode> out = new ArrayList<>();
        for (JsonNode course : courses) {
            if (!course.has("name") || course.path("access_restricted_by_date").asBoolean(false)) {
                continue;
            }
            if (!course.has("id")) {
                continue;
            }
            long courseId = course.get("id").asLong();
            String courseName = course.get("name").asText();
            try {
                JsonNode assigns = client.get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/courses/{id}/assignments")
                                .queryParam("bucket", "upcoming")
                                .queryParam("per_page", 100)
                                .build(courseId))
                        .retrieve()
                        .body(JsonNode.class);
                if (assigns == null || !assigns.isArray()) {
                    continue;
                }
                for (JsonNode a : assigns) {
                    if (!(a instanceof com.fasterxml.jackson.databind.node.ObjectNode obj)) {
                        continue;
                    }
                    obj.put("_course_id", courseId);
                    obj.put("_course_name", courseName);
                    out.add(obj);
                }
            } catch (HttpStatusCodeException ignored) {
                // skip course if assignments forbidden
            }
        }
        return out;
    }

    private String canvasBase() {
        String url = props.canvasBaseUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("CANVAS_BASE_URL is not set");
        }
        return url.replaceAll("/$", "") + "/api/v1";
    }

    private String canvasToken() {
        String t = props.canvasAccessToken();
        if (t == null || t.isBlank()) {
            throw new IllegalStateException("CANVAS_ACCESS_TOKEN is not set");
        }
        return t;
    }
}
