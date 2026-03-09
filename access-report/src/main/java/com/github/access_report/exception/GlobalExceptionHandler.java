package com.github.access_report.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

/**
 * Centralized error handler for all controllers.
 *
 * Without this:
 *   Client gets Spring's default "Whitelabel Error Page" or a raw stack trace.
 *
 * With this:
 *   Client always gets a clean, consistent JSON error body:
 *   {
 *     "error":     "GitHub API Error",
 *     "message":   "GitHub token is invalid or expired...",
 *     "timestamp": "2024-03-07T10:30:00Z"
 *   }
 *
 * @RestControllerAdvice — applies to all @RestController classes automatically.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles all GitHub API failures.
     * Returns 502 Bad Gateway — meaning "our server tried to call GitHub and failed".
     */
    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<Map<String, Object>> handleGitHubApiException(GitHubApiException ex) {
        log.error("GitHub API error: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(buildError("GitHub API Error", ex.getMessage()));
    }

    /**
     * Catches everything else — prevents stack traces from leaking to clients.
     * Returns 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("Internal Server Error",
                        "An unexpected error occurred. Please check server logs."));
    }

    private Map<String, Object> buildError(String error, String message) {
        return Map.of(
                "error",     error,
                "message",   message,
                "timestamp", Instant.now().toString()
        );
    }
}