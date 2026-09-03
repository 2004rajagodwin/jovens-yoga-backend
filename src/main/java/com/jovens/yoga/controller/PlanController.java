package com.jovens.yoga.controller;

import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.dto.response.PlanResponse;
import com.jovens.yoga.service.PlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping("/active")
    public ApiResponse<List<PlanResponse>> getActivePlans() {
        return ApiResponse.success("Active plans retrieved.", planService.getActivePlans());
    }

    @GetMapping("/{id}")
    public ApiResponse<PlanResponse> getPlan(@PathVariable Long id) {
        return ApiResponse.success("Plan retrieved.", planService.getPlanById(id));
    }
}
