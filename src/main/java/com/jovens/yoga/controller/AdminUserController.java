package com.jovens.yoga.controller;

import com.jovens.yoga.dto.response.AdminUserDetailResponse;
import com.jovens.yoga.dto.response.AdminUserResponse;
import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.service.AdminUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success("Users retrieved.", adminUserService.listUsers(search, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserDetailResponse> getUser(@PathVariable Long id) {
        return ApiResponse.success("User retrieved.", adminUserService.getUser(id));
    }
}
