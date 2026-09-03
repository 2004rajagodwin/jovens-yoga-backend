package com.jovens.yoga.controller;

import com.jovens.yoga.dto.response.AdminTrialResponse;
import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.enums.TrialStatus;
import com.jovens.yoga.service.AdminTrialService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/trials")
public class AdminTrialController {

    private final AdminTrialService adminTrialService;

    public AdminTrialController(AdminTrialService adminTrialService) {
        this.adminTrialService = adminTrialService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminTrialResponse>> listTrials(
            @RequestParam(required = false) TrialStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success("Trials retrieved.", adminTrialService.listTrials(status, search, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminTrialResponse> getTrial(@PathVariable Long id) {
        return ApiResponse.success("Trial retrieved.", adminTrialService.getTrial(id));
    }
}
