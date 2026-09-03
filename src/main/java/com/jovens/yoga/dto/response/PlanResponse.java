package com.jovens.yoga.dto.response;

import java.util.List;

public record PlanResponse(
        Long id,
        String name,
        String description,
        String imageUrl,
        String planType,
        String currency,
        Integer trialDurationDays,
        boolean featured,
        String badgeText,
        boolean active,
        int displayOrder,
        List<PlanDurationResponse> durations,
        List<PlanFeatureResponse> features
) {
}
