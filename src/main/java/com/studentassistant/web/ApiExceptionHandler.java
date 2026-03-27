package com.studentassistant.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalStateException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "Bad request";
        if (msg.contains("not set")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", msg));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("detail", msg));
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> canvasUpstream(RestClientException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("detail", "Canvas error: " + e.getMessage()));
    }
}
