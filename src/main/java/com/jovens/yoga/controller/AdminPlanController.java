package com.jovens.yoga.controller;

import com.jovens.yoga.dto.request.AdminPlanRequest;
import com.jovens.yoga.dto.request.ReorderRequest;
import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.dto.response.PlanResponse;
import com.jovens.yoga.service.PlanService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/plans")
public class AdminPlanController {

    private final PlanService planService;

    public AdminPlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ApiResponse<List<PlanResponse>> getAllPlans() {
        return ApiResponse.success("Plans retrieved.", planService.getAllPlansForAdmin());
    }

    @GetMapping("/{id}")
    public ApiResponse<PlanResponse> getPlan(@PathVariable Long id) {
        return ApiResponse.success("Plan retrieved.", planService.getPlanById(id));
    }

    @PostMapping
    public ApiResponse<PlanResponse> createPlan(@Valid @RequestBody AdminPlanRequest request) {
        return ApiResponse.success("Plan created.", planService.createPlan(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<PlanResponse> updatePlan(@PathVariable Long id, @Valid @RequestBody AdminPlanRequest request) {
        return ApiResponse.success("Plan updated.", planService.updatePlan(id, request));
    }

    @PatchMapping("/{id}/active")
    public ApiResponse<Void> setActive(@PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        planService.setActive(id, Boolean.TRUE.equals(body.get("active")));
        return ApiResponse.success("Plan status updated.", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePlan(@PathVariable Long id) {
        planService.deletePlan(id);
        return ApiResponse.success("Plan deleted.", null);
    }

    @PostMapping("/reorder")
    public ApiResponse<Void> reorderPlans(@Valid @RequestBody ReorderRequest request) {
        planService.reorderPlans(request);
        return ApiResponse.success("Plans reordered.", null);
    }
}
