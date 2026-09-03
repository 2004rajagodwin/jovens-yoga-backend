package com.jovens.yoga.service;

import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.entity.User;

public interface NotificationService {

    /** Records and dispatches (email) the trial activation notifications for a user. */
    void recordTrialActivation(User user, Trial trial);

    /** Records and dispatches the day-N trial reminder notifications, idempotent per trial+day. */
    void recordTrialReminder(User user, Trial trial, int dayIndex);

    /** Records and dispatches the trial expiry notifications. */
    void recordTrialExpiry(User user, Trial trial);

    /**
     * Records and dispatches the paid purchase confirmation notification. Callers must only
     * invoke this after the order has been verified PAID by the Stripe webhook — never on
     * checkout-session creation or an unverified frontend redirect.
     */
    void recordPaidPurchase(User user, CustomerOrder order);

    /**
     * Records and dispatches the "trial converted to paid" notification, fired when a
     * trial's Stripe subscription successfully auto-charges after the trial period ends.
     * Callers must only invoke this after the recurring invoice charge has been verified
     * PAID by the Stripe webhook. Idempotent per {@code payment}, so it fires once per
     * billing cycle rather than once per trial.
     */
    void recordTrialConversionPurchase(User user, Trial trial, Payment payment);

    /**
     * Re-attempts delivery of a FAILED email notification. Rejects retrying a notification
     * that is not FAILED (in particular, an already-SENT one is never re-sent).
     */
    void retryEmail(Long notificationId);
}
