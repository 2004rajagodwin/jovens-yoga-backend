package com.jovens.yoga.dto.response;

import java.time.LocalDateTime;

public record AdminNotificationResponse(
        Long id,
        String customerName,
        String type,
        String channel,
        String status,
        String eventKey,
        String providerReference,
        String failureReason,
        LocalDateTime sentAt,
        LocalDateTime createdAt
) {
}
