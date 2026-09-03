package com.jovens.yoga.service;

import com.jovens.yoga.dto.response.CheckoutResponse;

public interface PaymentService {

    /** Creates a Stripe Checkout Session for a still-PENDING order and stores the session id. */
    CheckoutResponse createCheckoutSession(String orderNumber);

    /**
     * Creates a Stripe subscription Checkout Session (with trial period) for a still-
     * TRIAL_PENDING_PAYMENT trial, resolved by its short-lived access token — never a raw
     * trial id, which must never be an authorization credential in this API.
     */
    CheckoutResponse createTrialCheckoutSession(String rawAccessToken);

    /**
     * Verifies and processes a Stripe webhook event. Authoritative source of truth for
     * payment/order state — never trust frontend redirects or client-supplied status.
     */
    void handleStripeWebhook(String payload, String signatureHeader);
}
