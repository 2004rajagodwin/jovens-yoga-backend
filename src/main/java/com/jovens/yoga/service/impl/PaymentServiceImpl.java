package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.response.CheckoutResponse;
import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.enums.OrderStatus;
import com.jovens.yoga.enums.PaymentStatus;
import com.jovens.yoga.enums.TrialStatus;
import com.jovens.yoga.exception.BusinessException;
import com.jovens.yoga.exception.PaymentException;
import com.jovens.yoga.exception.ResourceNotFoundException;
import com.jovens.yoga.integration.stripe.StripeCheckoutClient;
import com.jovens.yoga.repository.OrderRepository;
import com.jovens.yoga.repository.PaymentRepository;
import com.jovens.yoga.repository.TrialRepository;
import com.jovens.yoga.security.AccessTokenGenerator;
import com.jovens.yoga.service.NotificationService;
import com.jovens.yoga.service.PaymentService;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private static final String EVENT_CHECKOUT_COMPLETED = "checkout.session.completed";
    private static final String EVENT_ASYNC_PAYMENT_SUCCEEDED = "checkout.session.async_payment_succeeded";
    private static final String EVENT_ASYNC_PAYMENT_FAILED = "checkout.session.async_payment_failed";
    private static final String EVENT_SESSION_EXPIRED = "checkout.session.expired";
    private static final String EVENT_CHARGE_REFUNDED = "charge.refunded";
    private static final String EVENT_INVOICE_PAYMENT_SUCCEEDED = "invoice.payment_succeeded";
    private static final String EVENT_INVOICE_PAYMENT_FAILED = "invoice.payment_failed";

    private static final String MODE_SUBSCRIPTION = "subscription";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TrialRepository trialRepository;
    private final StripeCheckoutClient stripeCheckoutClient;
    private final NotificationService notificationService;
    private final int defaultTrialDurationDays;
    private final AccessTokenGenerator accessTokenGenerator;

    /** Same short lifetime as the token issued by TrialServiceImpl.checkEligibility. */
    private static final long ACCESS_TOKEN_TTL_MINUTES = 30;

    public PaymentServiceImpl(OrderRepository orderRepository,
                               PaymentRepository paymentRepository,
                               TrialRepository trialRepository,
                               StripeCheckoutClient stripeCheckoutClient,
                               NotificationService notificationService,
                               @Value("${app.trial.default-duration-days}") int defaultTrialDurationDays,
                               AccessTokenGenerator accessTokenGenerator) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.trialRepository = trialRepository;
        this.stripeCheckoutClient = stripeCheckoutClient;
        this.notificationService = notificationService;
        this.defaultTrialDurationDays = defaultTrialDurationDays;
        this.accessTokenGenerator = accessTokenGenerator;
    }

    @Override
    @Transactional
    public CheckoutResponse createCheckoutSession(String orderNumber) {
        CustomerOrder order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new PaymentException("This order is no longer awaiting payment.");
        }

        Session session = stripeCheckoutClient.createCheckoutSession(order);
        order.setStripeCheckoutSessionId(session.getId());

        return new CheckoutResponse(order.getOrderNumber(), session.getUrl());
    }

    @Override
    @Transactional
    public CheckoutResponse createTrialCheckoutSession(String rawAccessToken) {
        // Resolves the trial by the same short-lived, cryptographically random token the
        // browser was handed at trial creation — a raw trial id is never accepted here. This
        // single lookup also gives us "token exists, hasn't expired, and belongs to exactly
        // one trial" for free — there is no separate ownership check needed because the
        // token IS the ownership proof.
        Trial trial = resolveByAccessToken(rawAccessToken);

        if (trial.getStatus() != TrialStatus.TRIAL_PENDING_PAYMENT) {
            // Covers every non-replay-able case in one check: already activated (webhook beat
            // us to it), already cancelled, or already expired — a checkout session must never
            // be created for any of those, and none of them can be turned into a second trial
            // or a second subscription through this endpoint.
            throw new BusinessException("This trial is not awaiting payment.", "TRIAL_NOT_PENDING", HttpStatus.BAD_REQUEST);
        }

        // Freshly issued for this checkout attempt — embedded only in the browser-facing
        // success/cancel URLs, never the trial's raw database id (closes the trial-detail IDOR).
        // Rotating it here (rather than reusing the token that authorized this call) also
        // means the token that got the user this far can't be replayed once Stripe's own
        // page has taken over.
        String rawUrlToken = accessTokenGenerator.generate();
        trial.setAccessTokenHash(accessTokenGenerator.hash(rawUrlToken));
        trial.setAccessTokenExpiresAt(LocalDateTime.now().plusMinutes(ACCESS_TOKEN_TTL_MINUTES));

        Session session = stripeCheckoutClient.createTrialSubscriptionSession(trial, rawUrlToken);
        trial.setStripeCheckoutSessionId(session.getId());

        // CheckoutResponse's first field is the order-flow's order number; it has no trial
        // equivalent the frontend needs (it only ever reads checkoutUrl here) — leave it null
        // rather than returning the trial's raw id in a response body for no reason.
        return new CheckoutResponse(null, session.getUrl());
    }

    @Override
    @Transactional
    public void handleStripeWebhook(String payload, String signatureHeader) {
        Event event = stripeCheckoutClient.verifyAndParseEvent(payload, signatureHeader);

        if (paymentRepository.existsByStripeEventId(event.getId())) {
            log.info("Stripe event {} already processed, ignoring duplicate.", event.getId());
            return;
        }

        switch (event.getType()) {
            case EVENT_CHECKOUT_COMPLETED, EVENT_ASYNC_PAYMENT_SUCCEEDED -> handleCheckoutSucceeded(event);
            case EVENT_ASYNC_PAYMENT_FAILED -> handlePaymentFailed(event);
            case EVENT_SESSION_EXPIRED -> handleSessionExpired(event);
            case EVENT_CHARGE_REFUNDED -> handleChargeRefunded(event);
            case EVENT_INVOICE_PAYMENT_SUCCEEDED -> handleInvoicePaymentSucceeded(event);
            case EVENT_INVOICE_PAYMENT_FAILED -> handleInvoicePaymentFailed(event);
            default -> log.debug("Unhandled Stripe event type: {}", event.getType());
        }
    }

    /**
     * checkout.session.completed / async_payment_succeeded cover both the order (Mode.PAYMENT)
     * and trial subscription (Mode.SUBSCRIPTION) flows — branch on the session mode before
     * doing anything else so the two flows stay fully independent.
     */
    private void handleCheckoutSucceeded(Event event) {
        Session session = deserializeSession(event);
        if (session == null) {
            return;
        }

        if (MODE_SUBSCRIPTION.equals(session.getMode())) {
            handleTrialCheckoutCompleted(event, session);
            return;
        }

        if (!"paid".equals(session.getPaymentStatus())) {
            log.debug("Ignoring event {} — session payment_status is not 'paid'.", event.getId());
            return;
        }

        CustomerOrder order = resolveOrder(session);
        if (order == null) {
            log.warn("Stripe event {} referenced an unknown order (session {}).", event.getId(), session.getId());
            return;
        }

        if (order.getStatus() == OrderStatus.PAID) {
            log.info("Order {} already PAID, recording event {} as a no-op duplicate.", order.getOrderNumber(), event.getId());
            recordPaymentEvent(order, event, session, PaymentStatus.PAID);
            return;
        }

        order.setStatus(OrderStatus.PAID);
        recordPaymentEvent(order, event, session, PaymentStatus.PAID);

        notificationService.recordPaidPurchase(order.getUser(), order);
        log.info("Order {} marked PAID via Stripe event {}.", order.getOrderNumber(), event.getId());
    }

    /**
     * Resolves the trial referenced by a completed subscription Checkout Session and
     * activates it. Trial period days / start-expiry are computed here (not at trial
     * creation time) because activation only happens once Stripe confirms the checkout.
     */
    private void handleTrialCheckoutCompleted(Event event, Session session) {
        Trial trial = resolveTrial(session);
        if (trial == null) {
            log.warn("Stripe event {} referenced an unknown trial (session {}).", event.getId(), session.getId());
            return;
        }

        // Only a still-pending trial should ever be activated here. Any other status —
        // already ACTIVE, already converted to TRIAL_EXPIRED, or CANCELLED — means this
        // event was already handled (or no longer applies) and must be a no-op. Guarding
        // only against TRIAL_ACTIVE was a real idempotency bug: a redelivered/replayed
        // checkout.session.completed event for a trial that had since converted to a paid
        // subscription would silently revert it back to a fresh TRIAL_ACTIVE state.
        if (trial.getStatus() != TrialStatus.TRIAL_PENDING_PAYMENT) {
            log.info("Trial {} is {} (not pending), ignoring duplicate/late event {}.",
                    trial.getId(), trial.getStatus(), event.getId());
            return;
        }

        int durationDays = trial.getPlan().getTrialDurationDays() != null
                ? trial.getPlan().getTrialDurationDays()
                : defaultTrialDurationDays;
        LocalDateTime now = LocalDateTime.now();

        trial.setTrialStartDate(now);
        trial.setTrialExpiryDate(now.plusDays(durationDays));
        trial.setStatus(TrialStatus.TRIAL_ACTIVE);
        trial.setStripeCustomerId(session.getCustomer());
        trial.setStripeSubscriptionId(session.getSubscription());
        trial.setStripeCheckoutSessionId(session.getId());

        notificationService.recordTrialActivation(trial.getUser(), trial);
        log.info("Trial {} marked ACTIVE via Stripe event {}.", trial.getId(), event.getId());
    }

    private void handlePaymentFailed(Event event) {
        Session session = deserializeSession(event);
        if (session == null || MODE_SUBSCRIPTION.equals(session.getMode())) {
            return;
        }
        CustomerOrder order = resolveOrder(session);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        order.setStatus(OrderStatus.FAILED);
        recordPaymentEvent(order, event, session, PaymentStatus.FAILED);
        log.info("Order {} marked FAILED via Stripe event {}.", order.getOrderNumber(), event.getId());
    }

    private void handleSessionExpired(Event event) {
        Session session = deserializeSession(event);
        if (session == null || MODE_SUBSCRIPTION.equals(session.getMode())) {
            return;
        }
        CustomerOrder order = resolveOrder(session);
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);
        log.info("Order {} marked CANCELLED (checkout session expired), event {}.", order.getOrderNumber(), event.getId());
    }

    private void handleChargeRefunded(Event event) {
        StripeObject stripeObject = deserializeEventObject(event);

        if (!(stripeObject instanceof Charge charge)) {
            log.warn("Could not deserialize Stripe event {} as a Charge.", event.getId());
            return;
        }

        Optional<Payment> originalPayment = paymentRepository
                .findFirstByStripePaymentIntentIdOrderByCreatedAtDesc(charge.getPaymentIntent());

        if (originalPayment.isEmpty()) {
            log.warn("Stripe refund event {} referenced an unknown payment intent {}.", event.getId(), charge.getPaymentIntent());
            return;
        }

        CustomerOrder order = originalPayment.get().getOrder();
        if (order == null) {
            log.warn("Stripe refund event {} matched a trial-subscription payment, not an order — skipping.", event.getId());
            return;
        }
        order.setStatus(OrderStatus.REFUNDED);

        Payment refundRecord = new Payment();
        refundRecord.setOrder(order);
        refundRecord.setStripeEventId(event.getId());
        refundRecord.setStripePaymentIntentId(charge.getPaymentIntent());
        refundRecord.setAmount(order.getAmount());
        refundRecord.setCurrency(order.getCurrency());
        refundRecord.setStatus(PaymentStatus.REFUNDED);

        try {
            paymentRepository.save(refundRecord);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Refund event {} already recorded, skipping duplicate.", event.getId());
        }

        log.info("Order {} marked REFUNDED via Stripe event {}.", order.getOrderNumber(), event.getId());
    }

    /**
     * Recurring subscription invoice charges (day-N+ of a trial's plan). The trial's own
     * $0 invoice at checkout time also fires this event — that one is skipped here since
     * trial activation is already handled by {@link #handleTrialCheckoutCompleted}.
     */
    private void handleInvoicePaymentSucceeded(Event event) {
        Invoice invoice = deserializeInvoice(event);
        String subscriptionId = invoice != null ? resolveInvoiceSubscriptionId(invoice) : null;
        if (subscriptionId == null) {
            return;
        }

        Trial trial = trialRepository.findByStripeSubscriptionId(subscriptionId).orElse(null);
        if (trial == null) {
            log.debug("Invoice event {} does not belong to a trial subscription, ignoring.", event.getId());
            return;
        }

        if (invoice.getAmountPaid() == null || invoice.getAmountPaid() == 0L) {
            log.debug("Invoice event {} is the $0 trial invoice, already handled at checkout.", event.getId());
            return;
        }

        Payment payment = new Payment();
        payment.setTrial(trial);
        payment.setStripeEventId(event.getId());
        payment.setAmount(fromSmallestCurrencyUnit(invoice.getAmountPaid()));
        payment.setCurrency(invoice.getCurrency());
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());

        try {
            paymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Invoice payment event {} already recorded, skipping duplicate.", event.getId());
            return;
        }

        // A successful non-zero invoice on this subscription unambiguously means the free
        // trial period has ended and converted to a paid plan — reflect that immediately
        // rather than waiting on the date-based TrialReminderScheduler to catch up later.
        // This also prevents that scheduler from later firing a contradictory "trial expired"
        // reminder for a user who already converted (it only queries TRIAL_ACTIVE rows).
        if (trial.getStatus() == TrialStatus.TRIAL_ACTIVE) {
            trial.setStatus(TrialStatus.TRIAL_EXPIRED);
            trialRepository.save(trial);
        }

        notificationService.recordTrialConversionPurchase(trial.getUser(), trial, payment);
        log.info("Recorded PAID subscription invoice for trial {} via Stripe event {}.", trial.getId(), event.getId());
    }

    private void handleInvoicePaymentFailed(Event event) {
        Invoice invoice = deserializeInvoice(event);
        String subscriptionId = invoice != null ? resolveInvoiceSubscriptionId(invoice) : null;
        if (subscriptionId == null) {
            return;
        }

        Trial trial = trialRepository.findByStripeSubscriptionId(subscriptionId).orElse(null);
        if (trial == null) {
            log.debug("Invoice event {} does not belong to a trial subscription, ignoring.", event.getId());
            return;
        }

        Payment payment = new Payment();
        payment.setTrial(trial);
        payment.setStripeEventId(event.getId());
        payment.setAmount(fromSmallestCurrencyUnit(invoice.getAmountDue() != null ? invoice.getAmountDue() : 0L));
        payment.setCurrency(invoice.getCurrency());
        payment.setStatus(PaymentStatus.FAILED);

        try {
            paymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Invoice payment event {} already recorded, skipping duplicate.", event.getId());
            return;
        }

        log.info("Recorded FAILED subscription invoice for trial {} via Stripe event {}.", trial.getId(), event.getId());
    }

    private void recordPaymentEvent(CustomerOrder order, Event event, Session session, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setStripeEventId(event.getId());
        payment.setStripeCheckoutSessionId(session.getId());
        payment.setStripePaymentIntentId(session.getPaymentIntent());
        payment.setAmount(order.getAmount());
        payment.setCurrency(order.getCurrency());
        payment.setStatus(status);
        if (status == PaymentStatus.PAID) {
            payment.setPaidAt(LocalDateTime.now());
        }

        try {
            paymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            // Unique constraint on stripe_event_id caught a concurrent duplicate webhook delivery.
            log.debug("Payment event {} already recorded, skipping duplicate.", event.getId());
        }
    }

    private CustomerOrder resolveOrder(Session session) {
        String orderNumber = session.getClientReferenceId();
        if (orderNumber == null && session.getMetadata() != null) {
            orderNumber = session.getMetadata().get("orderNumber");
        }

        Optional<CustomerOrder> byNumber = orderNumber != null ? orderRepository.findByOrderNumber(orderNumber) : Optional.empty();
        return byNumber.orElseGet(() -> orderRepository.findByStripeCheckoutSessionId(session.getId()).orElse(null));
    }

    private Trial resolveTrial(Session session) {
        String trialIdValue = session.getClientReferenceId();
        if (trialIdValue == null && session.getMetadata() != null) {
            trialIdValue = session.getMetadata().get("trialId");
        }
        if (trialIdValue == null) {
            return null;
        }

        try {
            return trialRepository.findById(Long.valueOf(trialIdValue)).orElse(null);
        } catch (NumberFormatException ex) {
            log.warn("Could not parse trial id from Stripe session {}: {}", session.getId(), trialIdValue);
            return null;
        }
    }

    /**
     * Resolves a browser-supplied access token to its trial. Same rule as TrialServiceImpl's
     * equivalent: a missing/forged/expired token all fail identically — never reveal which,
     * that would itself leak information to an attacker probing this endpoint.
     */
    private Trial resolveByAccessToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResourceNotFoundException("Trial access token is missing or invalid.");
        }
        String hash = accessTokenGenerator.hash(rawToken);
        return trialRepository.findByAccessTokenHashAndAccessTokenExpiresAtAfter(hash, LocalDateTime.now())
                .orElseThrow(() -> new ResourceNotFoundException("Trial access token is missing or invalid."));
    }

    /**
     * Prefers the version-matched {@code getObject()}, but falls back to
     * {@code deserializeUnsafe()} when the connected Stripe account's API version is
     * newer than this SDK's bundled model definitions — {@code getObject()} returns
     * empty in that case even though the raw event payload is perfectly valid.
     */
    private Session deserializeSession(Event event) {
        StripeObject stripeObject = deserializeEventObject(event);
        if (!(stripeObject instanceof Session session)) {
            log.warn("Could not deserialize Stripe event {} as a checkout Session.", event.getId());
            return null;
        }
        return session;
    }

    private Invoice deserializeInvoice(Event event) {
        StripeObject stripeObject = deserializeEventObject(event);
        if (!(stripeObject instanceof Invoice invoice)) {
            log.warn("Could not deserialize Stripe event {} as an Invoice.", event.getId());
            return null;
        }
        return invoice;
    }

    private StripeObject deserializeEventObject(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        Optional<StripeObject> stripeObject = deserializer.getObject();
        if (stripeObject.isPresent()) {
            return stripeObject.get();
        }

        try {
            return deserializer.deserializeUnsafe();
        } catch (Exception ex) {
            log.warn("Could not deserialize Stripe event {} data object even with deserializeUnsafe(): {}",
                    event.getId(), ex.getMessage());
            return null;
        }
    }

    /** Converts a Stripe amount in the smallest currency unit (e.g. cents) back to a decimal amount. */
    private BigDecimal fromSmallestCurrencyUnit(long amount) {
        return BigDecimal.valueOf(amount, 2);
    }

    /**
     * In stripe-java 29.x, an Invoice no longer exposes {@code getSubscription()} directly —
     * the subscription link moved under {@code invoice.getParent().getSubscriptionDetails()}.
     */
    private String resolveInvoiceSubscriptionId(Invoice invoice) {
        Invoice.Parent parent = invoice.getParent();
        if (parent == null || parent.getSubscriptionDetails() == null) {
            return null;
        }
        return parent.getSubscriptionDetails().getSubscription();
    }
}
