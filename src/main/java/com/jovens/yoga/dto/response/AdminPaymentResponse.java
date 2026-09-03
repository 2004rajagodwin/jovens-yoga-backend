package com.jovens.yoga.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPaymentResponse(
        Long id,
        String orderNumber,
        String customerName,
        String stripeCheckoutSessionId,
        String stripePaymentIntentId,
        BigDecimal amount,
        String currency,
        String status,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        String source,
        Long trialId
) {
}
