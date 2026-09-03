package com.jovens.yoga.dto.response;

import java.time.LocalDateTime;

public record AdminTrialResponse(
        Long id,
        String customerName,
        String email,
        String mobileNumber,
        String planName,
        LocalDateTime trialStartDate,
        LocalDateTime trialExpiryDate,
        String status,
        int lastReminderDayIndex,
        LocalDateTime createdAt,
        String planDurationLabel,
        String slotLabel,
        java.time.LocalDate slotDate,
        String stripeSubscriptionId,
        String stripeCustomerId
) {
}
