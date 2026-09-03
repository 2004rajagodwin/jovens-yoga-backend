package com.jovens.yoga.mapper;

import com.jovens.yoga.dto.response.PlanDurationResponse;
import com.jovens.yoga.dto.response.PlanFeatureResponse;
import com.jovens.yoga.dto.response.PlanResponse;
import com.jovens.yoga.entity.Plan;
import com.jovens.yoga.entity.PlanDuration;
import com.jovens.yoga.entity.PlanFeature;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class PlanMapper {

    public PlanResponse toResponse(Plan plan) {
        List<PlanDurationResponse> durations = plan.getDurations().stream()
                .sorted(Comparator.comparingInt(PlanDuration::getDisplayOrder))
                .map(this::toResponse)
                .toList();

        List<PlanFeatureResponse> features = plan.getFeatures().stream()
                .sorted(Comparator.comparingInt(PlanFeature::getDisplayOrder))
                .map(this::toResponse)
                .toList();

        return new PlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getDescription(),
                plan.getImageUrl(),
                plan.getPlanType().name(),
                plan.getCurrency(),
                plan.getTrialDurationDays(),
                plan.isFeatured(),
                plan.getBadgeText(),
                plan.isActive(),
                plan.getDisplayOrder(),
                durations,
                features
        );
    }

    public PlanDurationResponse toResponse(PlanDuration duration) {
        return new PlanDurationResponse(
                duration.getId(),
                duration.getDurationLabel(),
                duration.getDurationValue(),
                duration.getDurationUnit().name(),
                duration.getPrice(),
                duration.getCurrency(),
                duration.getDisplayOrder(),
                duration.isActive()
        );
    }

    public PlanFeatureResponse toResponse(PlanFeature feature) {
        return new PlanFeatureResponse(
                feature.getId(),
                feature.getFeatureText(),
                feature.getDisplayOrder(),
                feature.isActive()
        );
    }
}
