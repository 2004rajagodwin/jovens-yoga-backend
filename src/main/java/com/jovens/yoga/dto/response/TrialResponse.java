package com.jovens.yoga.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record TrialResponse(
        Long id,
        String status,
        LocalDateTime trialStartDate,
        LocalDateTime trialExpiryDate,
        Long planId,
        String planName,
        Long userId,
        String firstName,
        String lastName,
        String email,
        Long planDurationId,
        String durationLabel,
        BigDecimal price,
        String currency,
        Long slotId,
        String slotLabel,
        LocalDate slotDate,
        LocalTime slotStartTime,
        String stripeSubscriptionId,
        BigDecimal paymentAmount,
        String paymentCurrency,
        LocalDateTime paymentDate,
        String paymentReference,
        /**
         * Populated ONLY by {@code createTrial} — a freshly issued, short-lived access token
         * the browser must use for every subsequent unauthenticated call for this trial (the
         * Stripe checkout-session request, the Thank You page, a later "already active"
         * check). Never populated on any other read — a trial's raw id must never appear
         * here, and echoing an already-used token back on unrelated reads is unnecessary.
         */
        String accessToken
) {
}
