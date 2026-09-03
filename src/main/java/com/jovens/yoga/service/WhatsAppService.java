package com.jovens.yoga.service;

import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.entity.User;

/**
 * Sends transactional WhatsApp messages for business events, mirroring {@link EmailService}'s
 * method shape 1:1. Implementations own the provider integration (AskEva) and template/param
 * assembly. Every method returns a provider reference on success, {@code null} when the
 * provider isn't configured (graceful no-op), and throws
 * {@link com.jovens.yoga.exception.WhatsAppDeliveryException} on any other failure — callers
 * decide how to record that outcome (see NotificationService), never letting a delivery
 * failure break the underlying business transaction.
 */
public interface WhatsAppService {

    String sendTrialActivationWhatsApp(User user, Trial trial);

    String sendTrialReminderWhatsApp(User user, Trial trial, int remainingDays);

    String sendTrialExpiryWhatsApp(User user, Trial trial);

    String sendPaidPurchaseWhatsApp(User user, CustomerOrder order);

    /** Sent when a trial's Stripe subscription successfully auto-charges after the trial period ends. */
    String sendTrialConversionPaidWhatsApp(User user, Trial trial, Payment payment);

    /**
     * Sends a one-time verification code. Takes a raw phone string (not a {@link User}) since
     * this can be sent before any {@code User} row exists — e.g. a brand-new customer's very
     * first submission of "User Details".
     */
    String sendOtpWhatsApp(String toPhoneE164, String firstName, String otpCode);
}
