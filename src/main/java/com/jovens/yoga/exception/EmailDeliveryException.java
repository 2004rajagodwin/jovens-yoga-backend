package com.jovens.yoga.exception;

/**
 * Internal-only exception for a failed SMTP send. Never surfaced to API clients —
 * callers catch this and record the failure on the {@code Notification} row instead.
 */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
