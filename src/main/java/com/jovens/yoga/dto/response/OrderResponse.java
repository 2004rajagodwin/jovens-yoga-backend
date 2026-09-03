package com.jovens.yoga.dto.response;

import java.math.BigDecimal;

public record OrderResponse(
        String orderNumber,
        String planName,
        String durationLabel,
        BigDecimal amount,
        String currency,
        String status,
        String customerName,
        String email
) {
}
