package com.jovens.yoga.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminPlanFeatureRequest(
        Long id,
        @NotBlank String featureText,
        int displayOrder,
        boolean active
) {
}
