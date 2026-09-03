package com.jovens.yoga.controller;

import com.jovens.yoga.dto.response.AdminDashboardResponse;
import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.service.AdminDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping
    public ApiResponse<AdminDashboardResponse> getDashboard() {
        return ApiResponse.success("Dashboard statistics retrieved.", adminDashboardService.getDashboard());
    }
}
