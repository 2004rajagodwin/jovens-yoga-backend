package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.response.AdminNotificationResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.entity.Notification;
import com.jovens.yoga.enums.NotificationChannel;
import com.jovens.yoga.enums.NotificationStatus;
import com.jovens.yoga.exception.ResourceNotFoundException;
import com.jovens.yoga.repository.NotificationRepository;
import com.jovens.yoga.service.AdminNotificationService;
import com.jovens.yoga.service.NotificationService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminNotificationServiceImpl implements AdminNotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public AdminNotificationServiceImpl(NotificationRepository notificationRepository, NotificationService notificationService) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminNotificationResponse> listNotifications(NotificationStatus status, NotificationChannel channel, Pageable pageable) {
        return PageResponse.from(notificationRepository.search(status, channel, pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminNotificationResponse getNotification(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));
        return toResponse(notification);
    }

    @Override
    public void retryNotification(Long id) {
        notificationService.retryEmail(id);
    }

    private AdminNotificationResponse toResponse(Notification notification) {
        return new AdminNotificationResponse(
                notification.getId(),
                notification.getUser() != null ? notification.getUser().getFirstName() + " " + notification.getUser().getLastName() : null,
                notification.getType().name(),
                notification.getChannel().name(),
                notification.getStatus().name(),
                notification.getEventKey(),
                notification.getProviderReference(),
                notification.getFailureReason(),
                notification.getSentAt(),
                notification.getCreatedAt()
        );
    }
}
