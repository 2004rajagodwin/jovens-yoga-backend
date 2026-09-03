package com.jovens.yoga.dto.response;

import java.math.BigDecimal;

public record PlanDurationResponse(
        Long id,
        String durationLabel,
        int durationValue,
        String durationUnit,
        BigDecimal price,
        String currency,
        int displayOrder,
        boolean active
) {
}
