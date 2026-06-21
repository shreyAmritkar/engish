package com.stylecommunicator.exception;

/**
 * Thrown when a user's response fails intent validation.
 * Produces errorCode: "INTENT_VALIDATION_FAILED" in the error response
 * so the frontend can distinguish it from generic 400s without
 * matching on message text.
 */
public class IntentValidationException extends RuntimeException {
    public IntentValidationException(String reason) {
        super(reason);
    }
}
