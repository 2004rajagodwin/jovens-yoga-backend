package com.jovens.yoga.exception;

/**
 * Internal-only exception for a failed AskEva WhatsApp send. Never surfaced to API clients —
 * callers catch this and record the failure on the {@code Notification} row instead.
 */
public class WhatsAppDeliveryException extends RuntimeException {
    public WhatsAppDeliveryException(String message) {
        super(message);
    }

    public WhatsAppDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
