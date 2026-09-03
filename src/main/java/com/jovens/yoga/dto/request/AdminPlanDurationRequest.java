package com.jovens.yoga.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdminPlanDurationRequest(
        Long id,
        @NotBlank String durationLabel,
        @Min(1) int durationValue,
        @NotBlank String durationUnit,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal price,
        @NotBlank String currency,
        int displayOrder,
        boolean active
) {
}
