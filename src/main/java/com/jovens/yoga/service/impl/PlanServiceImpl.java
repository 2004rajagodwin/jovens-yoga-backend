package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.request.AdminPlanDurationRequest;
import com.jovens.yoga.dto.request.AdminPlanFeatureRequest;
import com.jovens.yoga.dto.request.AdminPlanRequest;
import com.jovens.yoga.dto.request.ReorderRequest;
import com.jovens.yoga.dto.response.PlanResponse;
import com.jovens.yoga.entity.Plan;
import com.jovens.yoga.entity.PlanDuration;
import com.jovens.yoga.entity.PlanFeature;
import com.jovens.yoga.enums.DurationUnit;
import com.jovens.yoga.enums.PlanType;
import com.jovens.yoga.exception.BusinessException;
import com.jovens.yoga.exception.ResourceNotFoundException;
import com.jovens.yoga.mapper.PlanMapper;
import com.jovens.yoga.repository.PlanRepository;
import com.jovens.yoga.service.PlanService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PlanServiceImpl implements PlanService {

    private final PlanRepository planRepository;
    private final PlanMapper planMapper;

    public PlanServiceImpl(PlanRepository planRepository, PlanMapper planMapper) {
        this.planRepository = planRepository;
        this.planMapper = planMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> getActivePlans() {
        return planRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(planMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> getAllPlansForAdmin() {
        return planRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(planMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PlanResponse getPlanById(Long id) {
        return planMapper.toResponse(findPlanOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Plan getActivePlanEntity(Long id) {
        Plan plan = findPlanOrThrow(id);
        if (!plan.isActive()) {
            throw new BusinessException("The selected plan is not currently available.", "PLAN_INACTIVE", HttpStatus.BAD_REQUEST);
        }
        return plan;
    }

    @Override
    @Transactional
    public PlanResponse createPlan(AdminPlanRequest request) {
        Plan plan = new Plan();
        applyRequest(plan, request);
        Plan saved = planRepository.save(plan);
        return planMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PlanResponse updatePlan(Long id, AdminPlanRequest request) {
        Plan plan = findPlanOrThrow(id);
        applyRequest(plan, request);
        return planMapper.toResponse(plan);
    }

    @Override
    @Transactional
    public void setActive(Long id, boolean active) {
        Plan plan = findPlanOrThrow(id);
        plan.setActive(active);
    }

    @Override
    @Transactional
    public void deletePlan(Long id) {
        Plan plan = findPlanOrThrow(id);
        planRepository.delete(plan);
    }

    @Override
    @Transactional
    public void reorderPlans(ReorderRequest request) {
        List<Plan> plans = planRepository.findAllById(request.orderedIds());
        Map<Long, Plan> byId = new HashMap<>();
        plans.forEach(p -> byId.put(p.getId(), p));

        int order = 0;
        for (Long id : request.orderedIds()) {
            Plan plan = byId.get(id);
            if (plan != null) {
                plan.setDisplayOrder(order++);
            }
        }
    }

    private Plan findPlanOrThrow(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found with id: " + id));
    }

    private void applyRequest(Plan plan, AdminPlanRequest request) {
        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setImageUrl(request.imageUrl());
        plan.setPlanType(parsePlanType(request.planType()));
        plan.setCurrency(request.currency());
        plan.setTrialDurationDays(request.trialDurationDays());
        plan.setFeatured(request.featured());
        plan.setBadgeText(request.badgeText());
        plan.setActive(request.active());
        plan.setDisplayOrder(request.displayOrder());

        syncDurations(plan, request.durations());
        syncFeatures(plan, request.features());
    }

    /**
     * Reconciles by id rather than clear-and-recreate: an existing duration referenced by
     * this id is updated in place (same managed entity), a null id creates a new one, and
     * any existing duration not present in the request is removed via orphanRemoval.
     * Blindly recreating every row would try to delete-and-reinsert durations that are
     * already referenced by historical orders (plan_duration_id is a NOT NULL FK there),
     * which would fail the whole update with a constraint violation.
     */
    private void syncDurations(Plan plan, List<AdminPlanDurationRequest> requests) {
        Map<Long, PlanDuration> existingById = plan.getDurations().stream()
                .filter(d -> d.getId() != null)
                .collect(Collectors.toMap(PlanDuration::getId, d -> d));

        List<PlanDuration> reconciled = new ArrayList<>();
        if (requests != null) {
            for (AdminPlanDurationRequest req : requests) {
                PlanDuration duration = req.id() != null ? existingById.get(req.id()) : null;
                if (duration == null) {
                    duration = new PlanDuration();
                    duration.setPlan(plan);
                }
                duration.setDurationLabel(req.durationLabel());
                duration.setDurationValue(req.durationValue());
                duration.setDurationUnit(parseDurationUnit(req.durationUnit()));
                duration.setPrice(req.price());
                duration.setCurrency(req.currency());
                duration.setDisplayOrder(req.displayOrder());
                duration.setActive(req.active());
                reconciled.add(duration);
            }
        }

        plan.getDurations().clear();
        plan.getDurations().addAll(reconciled);
    }

    /** Same reconcile-by-id approach as {@link #syncDurations}, and for the same reason. */
    private void syncFeatures(Plan plan, List<AdminPlanFeatureRequest> requests) {
        Map<Long, PlanFeature> existingById = plan.getFeatures().stream()
                .filter(f -> f.getId() != null)
                .collect(Collectors.toMap(PlanFeature::getId, f -> f));

        List<PlanFeature> reconciled = new ArrayList<>();
        if (requests != null) {
            for (AdminPlanFeatureRequest req : requests) {
                PlanFeature feature = req.id() != null ? existingById.get(req.id()) : null;
                if (feature == null) {
                    feature = new PlanFeature();
                    feature.setPlan(plan);
                }
                feature.setFeatureText(req.featureText());
                feature.setDisplayOrder(req.displayOrder());
                feature.setActive(req.active());
                reconciled.add(feature);
            }
        }

        plan.getFeatures().clear();
        plan.getFeatures().addAll(reconciled);
    }

    private PlanType parsePlanType(String value) {
        try {
            return PlanType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid plan type: " + value, "INVALID_PLAN_TYPE", HttpStatus.BAD_REQUEST);
        }
    }

    private DurationUnit parseDurationUnit(String value) {
        try {
            return DurationUnit.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid duration unit: " + value, "INVALID_DURATION_UNIT", HttpStatus.BAD_REQUEST);
        }
    }
}
