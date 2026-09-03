package com.jovens.yoga.service;

import com.jovens.yoga.dto.response.AdminNotificationResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.enums.NotificationChannel;
import com.jovens.yoga.enums.NotificationStatus;
import org.springframework.data.domain.Pageable;

public interface AdminNotificationService {
    PageResponse<AdminNotificationResponse> listNotifications(NotificationStatus status, NotificationChannel channel, Pageable pageable);
    AdminNotificationResponse getNotification(Long id);
    void retryNotification(Long id);
}
