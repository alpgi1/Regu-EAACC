package com.regu.orchestration.exception;

/**
 * Thrown when Stage 2 (Annex IV gap assessment) is attempted on a session
 * whose risk classification is not high-risk. Stage 2 is only applicable
 * when risk_category = "high". Maps to HTTP 400 Bad Request.
 */
public class Stage2NotApplicableException extends RuntimeException {

    public Stage2NotApplicableException(String message) {
        super(message);
    }

    public Stage2NotApplicableException(String message, Throwable cause) {
        super(message, cause);
    }
}
