package com.jovens.yoga.integration.stripe;

import com.jovens.yoga.config.StripeProperties;
import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.entity.PlanDuration;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.enums.DurationUnit;
import com.jovens.yoga.exception.PaymentException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The only class that talks to the Stripe SDK directly. Keeps checkout-session
 * creation and webhook-signature verification isolated from business logic.
 */
@Component
public class StripeCheckoutClient {

    private static final Logger log = LoggerFactory.getLogger(StripeCheckoutClient.class);

    private final StripeProperties stripeProperties;
    private final int defaultTrialDurationDays;

    public StripeCheckoutClient(StripeProperties stripeProperties,
                                 @Value("${app.trial.default-duration-days}") int defaultTrialDurationDays) {
        this.stripeProperties = stripeProperties;
        this.defaultTrialDurationDays = defaultTrialDurationDays;
    }

    public Session createCheckoutSession(CustomerOrder order) {
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setClientReferenceId(order.getOrderNumber())
                .putMetadata("orderNumber", order.getOrderNumber())
                .setSuccessUrl(stripeProperties.getSuccessUrl() + "?order=" + order.getOrderNumber() + "&session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(stripeProperties.getCancelUrl() + "?order=" + order.getOrderNumber())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(order.getCurrency().toLowerCase())
                                .setUnitAmount(toSmallestCurrencyUnit(order.getAmount()))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(order.getPlanNameSnapshot() + " - " + order.getDurationLabelSnapshot())
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            return Session.create(params);
        } catch (StripeException ex) {
            log.warn("Stripe checkout session creation failed for order {}: {}", order.getOrderNumber(), ex.getMessage());
            throw new PaymentException("Unable to start checkout. Please try again.");
        }
    }

    /**
     * Builds a SUBSCRIPTION-mode Checkout Session with a trial period for the given Trial.
     * The price is always resolved server-side from {@code trial.getPlanDuration()} —
     * never from anything the frontend could have supplied.
     *
     * @param rawAccessToken the trial's freshly issued, short-lived, cryptographically random
     *                        access token — used ONLY in the browser-facing success/cancel URLs.
     *                        {@code clientReferenceId}/metadata still carry the real trial id,
     *                        since those are server-to-server (Stripe -> our webhook) and never
     *                        reach the browser, so they aren't part of the IDOR surface.
     */
    public Session createTrialSubscriptionSession(Trial trial, String rawAccessToken) {
        PlanDuration duration = trial.getPlanDuration();
        int trialPeriodDays = trial.getPlan().getTrialDurationDays() != null
                ? trial.getPlan().getTrialDurationDays()
                : defaultTrialDurationDays;

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setClientReferenceId(String.valueOf(trial.getId()))
                .putMetadata("trialId", String.valueOf(trial.getId()))
                .setSuccessUrl(stripeProperties.getSuccessUrl() + "?type=trial&token=" + rawAccessToken + "&session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(stripeProperties.getCancelUrl() + "?type=trial&token=" + rawAccessToken)
                .setSubscriptionData(SessionCreateParams.SubscriptionData.builder()
                        .setTrialPeriodDays((long) trialPeriodDays)
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(duration.getCurrency().toLowerCase())
                                .setUnitAmount(toSmallestCurrencyUnit(duration.getPrice()))
                                .setRecurring(SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                        .setInterval(toStripeInterval(duration.getDurationUnit()))
                                        .setIntervalCount((long) duration.getDurationValue())
                                        .build())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(trial.getPlan().getName() + " - " + duration.getDurationLabel())
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            return Session.create(params);
        } catch (StripeException ex) {
            log.warn("Stripe trial subscription session creation failed for trial {}: {}", trial.getId(), ex.getMessage());
            throw new PaymentException("Unable to start trial checkout. Please try again.");
        }
    }

    private SessionCreateParams.LineItem.PriceData.Recurring.Interval toStripeInterval(DurationUnit unit) {
        return switch (unit) {
            case DAY -> SessionCreateParams.LineItem.PriceData.Recurring.Interval.DAY;
            case MONTH -> SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH;
            case YEAR -> SessionCreateParams.LineItem.PriceData.Recurring.Interval.YEAR;
        };
    }

    public Event verifyAndParseEvent(String payload, String signatureHeader) {
        try {
            return Webhook.constructEvent(payload, signatureHeader, stripeProperties.getWebhookSecret());
        } catch (SignatureVerificationException ex) {
            log.warn("Stripe webhook signature verification failed: {}", ex.getMessage());
            throw new PaymentException("Invalid webhook signature.");
        }
    }

    private long toSmallestCurrencyUnit(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .longValueExact();
    }
}
