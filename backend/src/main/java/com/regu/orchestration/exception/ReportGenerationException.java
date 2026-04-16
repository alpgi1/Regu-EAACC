package com.regu.orchestration.exception;

/**
 * Thrown when report generation fails — either the LLM returned invalid output
 * after retries, or citation validation failed beyond the acceptable threshold.
 * Maps to HTTP 502 Bad Gateway.
 */
public class ReportGenerationException extends RuntimeException {

    public ReportGenerationException(String message) {
        super(message);
    }

    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
