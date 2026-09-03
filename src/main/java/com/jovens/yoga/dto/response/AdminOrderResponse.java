package com.jovens.yoga.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminOrderResponse(
        String orderNumber,
        String customerName,
        String email,
        String planName,
        String durationLabel,
        BigDecimal amount,
        String currency,
        String status,
        String stripeCheckoutSessionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
