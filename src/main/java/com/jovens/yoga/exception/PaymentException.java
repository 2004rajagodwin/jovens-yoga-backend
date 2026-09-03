package com.jovens.yoga.exception;

import org.springframework.http.HttpStatus;

public class PaymentException extends BusinessException {
    public PaymentException(String message) {
        super(message, "PAYMENT_ERROR", HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
