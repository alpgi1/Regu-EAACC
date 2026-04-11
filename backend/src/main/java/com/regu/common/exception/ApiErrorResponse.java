package com.regu.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * Standard error response returned by all REGU API endpoints when an exception
 * occurs. Every field except the error details list is always present.
 *
 * <p>The shape is stable and forms part of the public API contract. Frontend
 * error handling relies on this structure.
 *
 * @param timestamp when the error was generated (ISO-8601 UTC)
 * @param status    HTTP status code
 * @param error     HTTP status reason phrase (e.g., "Bad Request")
 * @param message   human-readable error message
 * @param path      request path that produced the error
 * @param details   optional list of field-level validation errors
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
    String timestamp,
    int status,
    String error,
    String message,
    String path,
    List<String> details
) {
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(Instant.now().toString(), status, error, message, path, null);
    }

    public static ApiErrorResponse of(int status, String error, String message, String path, List<String> details) {
        return new ApiErrorResponse(Instant.now().toString(), status, error, message, path, details);
    }
}
