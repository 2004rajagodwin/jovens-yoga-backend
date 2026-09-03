package com.jovens.yoga.controller;

import com.jovens.yoga.dto.request.AdminLoginRequest;
import com.jovens.yoga.dto.response.AdminLoginResponse;
import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminAuthService adminAuthService;

    public AuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/admin/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.success("Login successful.", adminAuthService.login(request));
    }
}
