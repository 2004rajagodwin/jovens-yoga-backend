package com.jovens.yoga.service;

import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.entity.User;

/**
 * Sends transactional emails for business events. Implementations own the provider
 * integration (SMTP, etc.) and template rendering. Every method throws
 * {@link com.jovens.yoga.exception.EmailDeliveryException} on failure and returns a
 * provider reference (e.g. Message-ID) on success — callers decide how to record that
 * outcome (see NotificationService), never letting a delivery failure break the
 * underlying business transaction.
 */
public interface EmailService {

    String sendTrialActivationEmail(User user, Trial trial);

    String sendTrialReminderEmail(User user, Trial trial, int remainingDays);

    String sendTrialExpiryEmail(User user, Trial trial);

    String sendPaidPurchaseEmail(User user, CustomerOrder order);

    /** Sent when a trial's Stripe subscription successfully auto-charges after the trial period ends. */
    String sendTrialConversionPaidEmail(User user, Trial trial, Payment payment);
}
