package com.jovens.yoga.controller;

import com.jovens.yoga.dto.response.AdminNotificationResponse;
import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.enums.NotificationChannel;
import com.jovens.yoga.enums.NotificationStatus;
import com.jovens.yoga.service.AdminNotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    public AdminNotificationController(AdminNotificationService adminNotificationService) {
        this.adminNotificationService = adminNotificationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminNotificationResponse>> listNotifications(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.success("Notifications retrieved.", adminNotificationService.listNotifications(status, channel, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminNotificationResponse> getNotification(@PathVariable Long id) {
        return ApiResponse.success("Notification retrieved.", adminNotificationService.getNotification(id));
    }

    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retryNotification(@PathVariable Long id) {
        adminNotificationService.retryNotification(id);
        return ApiResponse.success("Notification retry attempted.", null);
    }
}
