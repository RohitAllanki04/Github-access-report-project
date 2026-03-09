package com.github.access_report.exception;

/**
 * Thrown when any GitHub API call fails.
 *
 * Covers:
 *   - 401 Unauthorized (bad/expired token)
 *   - 403 Forbidden (missing scopes or rate limit hit)
 *   - 404 Not Found (repo deleted, wrong org name)
 *   - Network timeouts
 *
 * Using a custom exception instead of the raw Spring/HTTP exceptions means:
 *   - Caller code doesn't need to know about HttpClientErrorException
 *   - GlobalExceptionHandler can catch this one type and handle all GitHub errors
 *   - Easier to add retry logic later if needed
 */
public class GitHubApiException extends RuntimeException {

    public GitHubApiException(String message) {
        super(message);
    }

    public GitHubApiException(String message, Throwable cause) {
        super(message, cause);
    }
}