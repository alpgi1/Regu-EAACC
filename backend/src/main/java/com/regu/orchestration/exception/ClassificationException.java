package com.regu.orchestration.exception;

/**
 * Thrown when LLM-based risk classification fails after exhausting all retries.
 * Maps to HTTP 502 Bad Gateway — the upstream LLM provider is the failing component.
 */
public class ClassificationException extends RuntimeException {

    public ClassificationException(String message) {
        super(message);
    }

    public ClassificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
