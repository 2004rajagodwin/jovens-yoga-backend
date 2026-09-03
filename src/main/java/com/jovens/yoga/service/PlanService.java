package com.jovens.yoga.service;

import com.jovens.yoga.dto.request.AdminPlanRequest;
import com.jovens.yoga.dto.request.ReorderRequest;
import com.jovens.yoga.dto.response.PlanResponse;
import com.jovens.yoga.entity.Plan;

import java.util.List;

public interface PlanService {

    List<PlanResponse> getActivePlans();

    List<PlanResponse> getAllPlansForAdmin();

    PlanResponse getPlanById(Long id);

    /** Returns the managed entity for internal use by other services (e.g. trial/order creation). */
    Plan getActivePlanEntity(Long id);

    PlanResponse createPlan(AdminPlanRequest request);

    PlanResponse updatePlan(Long id, AdminPlanRequest request);

    void setActive(Long id, boolean active);

    void deletePlan(Long id);

    void reorderPlans(ReorderRequest request);
}
