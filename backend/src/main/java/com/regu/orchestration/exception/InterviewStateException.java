package com.regu.orchestration.exception;

/**
 * Thrown when an action is attempted on an interview session that is in
 * the wrong lifecycle state — e.g. submitting an answer after classification
 * is already complete, or starting Stage 2 before Stage 1 is done.
 * Maps to HTTP 409 Conflict.
 */
public class InterviewStateException extends RuntimeException {

    public InterviewStateException(String message) {
        super(message);
    }

    public InterviewStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
