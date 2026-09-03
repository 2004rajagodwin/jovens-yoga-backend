package com.jovens.yoga.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AdminPlanRequest(
        @NotBlank String name,
        String description,
        String imageUrl,
        @NotNull String planType,
        @NotBlank String currency,
        Integer trialDurationDays,
        boolean featured,
        String badgeText,
        boolean active,
        int displayOrder,
        @Valid List<AdminPlanDurationRequest> durations,
        @Valid List<AdminPlanFeatureRequest> features
) {
}
