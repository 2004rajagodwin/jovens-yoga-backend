package com.jovens.yoga.service;

import com.jovens.yoga.dto.response.CheckoutResponse;
import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.entity.Plan;
import com.jovens.yoga.entity.PlanDuration;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.entity.User;
import com.jovens.yoga.enums.DurationUnit;
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
import com.jovens.yoga.service.impl.PaymentServiceImpl;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TrialRepository trialRepository;

    @Mock
    private StripeCheckoutClient stripeCheckoutClient;

    @Mock
    private NotificationService notificationService;

    private final com.jovens.yoga.security.AccessTokenGenerator accessTokenGenerator = new com.jovens.yoga.security.AccessTokenGenerator();

    private PaymentServiceImpl paymentService;

    private PaymentServiceImpl service() {
        return new PaymentServiceImpl(orderRepository, paymentRepository, trialRepository, stripeCheckoutClient, notificationService, 5, accessTokenGenerator);
    }

    private CustomerOrder pendingOrder(String orderNumber) {
        User user = new User();
        user.setId(1L);
        user.setFirstName("Alice");
        user.setLastName("Smith");
        user.setEmail("alice@example.com");

        CustomerOrder order = new CustomerOrder();
        order.setId(10L);
        order.setOrderNumber(orderNumber);
        order.setUser(user);
        order.setPlanNameSnapshot("Premium");
        order.setDurationLabelSnapshot("Per Month");
        order.setAmount(new BigDecimal("59.00"));
        order.setCurrency("USD");
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    private Event mockEvent(String id, String type, StripeObject dataObject) {
        Event event = mock(Event.class);
        lenientReturn(event, id, type);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(dataObject));
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        return event;
    }

    private void lenientReturn(Event event, String id, String type) {
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn(type);
    }

    private Session paidSession(String orderNumber) {
        Session session = new Session();
        session.setId("cs_test_123");
        session.setPaymentStatus("paid");
        session.setClientReferenceId(orderNumber);
        session.setPaymentIntent("pi_test_123");
        Map<String, String> metadata = new HashMap<>();
        metadata.put("orderNumber", orderNumber);
        session.setMetadata(metadata);
        return session;
    }

    private Trial pendingTrial() {
        Plan plan = new Plan();
        plan.setId(2L);
        plan.setName("Standard");

        PlanDuration duration = new PlanDuration();
        duration.setId(2L);
        duration.setPlan(plan);
        duration.setDurationLabel("Per Month");
        duration.setDurationValue(1);
        duration.setDurationUnit(DurationUnit.MONTH);
        duration.setPrice(new BigDecimal("29.00"));
        duration.setCurrency("USD");

        User user = new User();
        user.setId(5L);
        user.setFirstName("Godwin");
        user.setLastName("S");
        user.setEmail("godwin@example.com");

        Trial trial = new Trial();
        trial.setId(24L);
        trial.setPlan(plan);
        trial.setPlanDuration(duration);
        trial.setUser(user);
        trial.setStatus(TrialStatus.TRIAL_PENDING_PAYMENT);
        return trial;
    }

    // -----------------------------------------------------------------------------------
    // Checkout-endpoint hardening: POST /api/payments/trials/access/{token}/checkout must
    // resolve by access token only — a raw trial id is never an authorization credential.
    // -----------------------------------------------------------------------------------

    @Test
    void trialCheckoutSucceedsForValidTokenOnAPendingTrial() {
        paymentService = service();
        Trial trial = pendingTrial();
        String rawToken = accessTokenGenerator.generate();
        trial.setAccessTokenHash(accessTokenGenerator.hash(rawToken));
        trial.setAccessTokenExpiresAt(java.time.LocalDateTime.now().plusMinutes(30));

        when(trialRepository.findByAccessTokenHashAndAccessTokenExpiresAtAfter(eq(trial.getAccessTokenHash()), any()))
                .thenReturn(Optional.of(trial));

        Session session = new Session();
        session.setId("cs_test_trial_1");
        session.setUrl("https://checkout.stripe.com/pay/cs_test_trial_1");
        when(stripeCheckoutClient.createTrialSubscriptionSession(eq(trial), anyString())).thenReturn(session);

        CheckoutResponse response = paymentService.createTrialCheckoutSession(rawToken);

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_trial_1");
        assertThat(trial.getStripeCheckoutSessionId()).isEqualTo("cs_test_trial_1");

        // Server resolves price/plan/currency from the trial's own PlanDuration — the caller
        // supplied nothing but the token, so there is no manipulable price/plan input at all.
        ArgumentCaptor<Trial> trialCaptor = ArgumentCaptor.forClass(Trial.class);
        verify(stripeCheckoutClient).createTrialSubscriptionSession(trialCaptor.capture(), anyString());
        assertThat(trialCaptor.getValue().getPlanDuration().getPrice()).isEqualByComparingTo("29.00");
        assertThat(trialCaptor.getValue().getPlan().getName()).isEqualTo("Standard");
    }

    @Test
    void trialCheckoutRejectsUnknownOrForgedToken() {
        paymentService = service();
        when(trialRepository.findByAccessTokenHashAndAccessTokenExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createTrialCheckoutSession("forged-token-never-issued"))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(stripeCheckoutClient);
    }

    @Test
    void trialCheckoutRejectsNullOrBlankToken() {
        paymentService = service();

        assertThatThrownBy(() -> paymentService.createTrialCheckoutSession(null))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> paymentService.createTrialCheckoutSession(""))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(stripeCheckoutClient);
    }

    @Test
    void trialCheckoutRejectsAlreadyActiveTrialPreventingDuplicateSubscription() {
        // Covers replay: once the webhook has activated the trial, the same (or any) token
        // for it must never be able to create a second Stripe subscription.
        paymentService = service();
        Trial trial = pendingTrial();
        trial.setStatus(TrialStatus.TRIAL_ACTIVE);
        String rawToken = accessTokenGenerator.generate();
        when(trialRepository.findByAccessTokenHashAndAccessTokenExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(trial));

        assertThatThrownBy(() -> paymentService.createTrialCheckoutSession(rawToken))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not awaiting payment");
        verifyNoInteractions(stripeCheckoutClient);
    }

    @Test
    void trialCheckoutRejectsCancelledTrial() {
        paymentService = service();
        Trial trial = pendingTrial();
        trial.setStatus(TrialStatus.CANCELLED);
        String rawToken = accessTokenGenerator.generate();
        when(trialRepository.findByAccessTokenHashAndAccessTokenExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(trial));

        assertThatThrownBy(() -> paymentService.createTrialCheckoutSession(rawToken))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(stripeCheckoutClient);
    }

    @Test
    void trialCheckoutRejectsExpiredTrial() {
        paymentService = service();
        Trial trial = pendingTrial();
        trial.setStatus(TrialStatus.TRIAL_EXPIRED);
        String rawToken = accessTokenGenerator.generate();
        when(trialRepository.findByAccessTokenHashAndAccessTokenExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(trial));

        assertThatThrownBy(() -> paymentService.createTrialCheckoutSession(rawToken))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(stripeCheckoutClient);
    }

    @Test
    void trialCheckoutResponseNeverReturnsTheTrialsRawDatabaseId() {
        paymentService = service();
        Trial trial = pendingTrial();
        String rawToken = accessTokenGenerator.generate();
        when(trialRepository.findByAccessTokenHashAndAccessTokenExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(trial));
        Session session = new Session();
        session.setId("cs_test_trial_2");
        session.setUrl("https://checkout.stripe.com/pay/cs_test_trial_2");
        when(stripeCheckoutClient.createTrialSubscriptionSession(any(), anyString())).thenReturn(session);

        CheckoutResponse response = paymentService.createTrialCheckoutSession(rawToken);

        assertThat(response.orderNumber()).isNotEqualTo(String.valueOf(trial.getId()));
    }

    @Test
    void createCheckoutSessionSucceedsForPendingOrder() {
        paymentService = service();
        CustomerOrder order = pendingOrder("ORD-1");
        when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));

        Session session = new Session();
        session.setId("cs_test_abc");
        session.setUrl("https://checkout.stripe.com/pay/cs_test_abc");
        when(stripeCheckoutClient.createCheckoutSession(order)).thenReturn(session);

        CheckoutResponse response = paymentService.createCheckoutSession("ORD-1");

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_abc");
        assertThat(order.getStripeCheckoutSessionId()).isEqualTo("cs_test_abc");
    }

    @Test
    void createCheckoutSessionRejectsNonPendingOrder() {
        paymentService = service();
        CustomerOrder order = pendingOrder("ORD-2");
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByOrderNumber("ORD-2")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.createCheckoutSession("ORD-2"))
                .isInstanceOf(PaymentException.class);
        verifyNoInteractions(stripeCheckoutClient);
    }

    @Test
    void createCheckoutSessionThrowsWhenOrderMissing() {
        paymentService = service();
        when(orderRepository.findByOrderNumber("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.createCheckoutSession("MISSING"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void invalidWebhookSignaturePropagatesAndTouchesNothing() {
        paymentService = service();
        when(stripeCheckoutClient.verifyAndParseEvent(anyString(), anyString()))
                .thenThrow(new PaymentException("Invalid webhook signature."));

        assertThatThrownBy(() -> paymentService.handleStripeWebhook("payload", "bad-sig"))
                .isInstanceOf(PaymentException.class);

        verifyNoInteractions(orderRepository, paymentRepository, notificationService);
    }

    @Test
    void validWebhookMarksOrderPaidAndTriggersNotification() {
        paymentService = service();
        CustomerOrder order = pendingOrder("ORD-3");
        Session session = paidSession("ORD-3");
        Event event = mockEvent("evt_1", "checkout.session.completed", session);

        when(stripeCheckoutClient.verifyAndParseEvent(anyString(), anyString())).thenReturn(event);
        when(paymentRepository.existsByStripeEventId("evt_1")).thenReturn(false);
        when(orderRepository.findByOrderNumber("ORD-3")).thenReturn(Optional.of(order));

        paymentService.handleStripeWebhook("payload", "sig");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(notificationService, times(1)).recordPaidPurchase(order.getUser(), order);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(captor.getValue().getStripeEventId()).isEqualTo("evt_1");
    }

    @Test
    void duplicateWebhookEventIsIgnored() {
        paymentService = service();
        Event event = mock(Event.class);
        when(event.getId()).thenReturn("evt_dup");
        when(stripeCheckoutClient.verifyAndParseEvent(anyString(), anyString())).thenReturn(event);
        when(paymentRepository.existsByStripeEventId("evt_dup")).thenReturn(true);

        paymentService.handleStripeWebhook("payload", "sig");

        verifyNoInteractions(orderRepository, notificationService);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void failedPaymentEventMarksOrderFailedWithoutNotification() {
        paymentService = service();
        CustomerOrder order = pendingOrder("ORD-4");
        Session session = paidSession("ORD-4");
        session.setPaymentStatus("unpaid");
        Event event = mockEvent("evt_2", "checkout.session.async_payment_failed", session);

        when(stripeCheckoutClient.verifyAndParseEvent(anyString(), anyString())).thenReturn(event);
        when(paymentRepository.existsByStripeEventId("evt_2")).thenReturn(false);
        when(orderRepository.findByOrderNumber("ORD-4")).thenReturn(Optional.of(order));

        paymentService.handleStripeWebhook("payload", "sig");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        verifyNoInteractions(notificationService);
    }

    @Test
    void expiredSessionEventMarksOrderCancelled() {
        paymentService = service();
        CustomerOrder order = pendingOrder("ORD-5");
        Session session = paidSession("ORD-5");
        Event event = mockEvent("evt_3", "checkout.session.expired", session);

        when(stripeCheckoutClient.verifyAndParseEvent(anyString(), anyString())).thenReturn(event);
        when(paymentRepository.existsByStripeEventId("evt_3")).thenReturn(false);
        when(orderRepository.findByOrderNumber("ORD-5")).thenReturn(Optional.of(order));

        paymentService.handleStripeWebhook("payload", "sig");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(notificationService);
    }

    @Test
    void replayedTrialCheckoutCompletedNeverReactivatesAConvertedTrial() {
        // Regression test for a real idempotency bug: a redelivered/replayed
        // checkout.session.completed event for a trial that had already converted to a
        // paid subscription (TRIAL_EXPIRED) must NOT revert it back to a fresh TRIAL_ACTIVE.
        paymentService = service();

        Plan plan = new Plan();
        plan.setId(1L);
        plan.setName("Standard");

        User user = new User();
        user.setId(1L);
        user.setFirstName("Alice");
        user.setLastName("Smith");

        Trial trial = new Trial();
        trial.setId(24L);
        trial.setPlan(plan);
        trial.setUser(user);
        trial.setStatus(TrialStatus.TRIAL_EXPIRED);
        var originalStart = java.time.LocalDateTime.of(2026, 9, 1, 17, 22, 43);
        var originalExpiry = java.time.LocalDateTime.of(2026, 9, 6, 17, 22, 43);
        trial.setTrialStartDate(originalStart);
        trial.setTrialExpiryDate(originalExpiry);

        Session session = new Session();
        session.setId("cs_test_trial");
        session.setMode("subscription");
        session.setClientReferenceId("24");
        session.setCustomer("cus_test");
        session.setSubscription("sub_test");
        Event event = mockEvent("evt_replay", "checkout.session.completed", session);

        when(stripeCheckoutClient.verifyAndParseEvent(anyString(), anyString())).thenReturn(event);
        when(paymentRepository.existsByStripeEventId("evt_replay")).thenReturn(false);
        when(trialRepository.findById(24L)).thenReturn(Optional.of(trial));

        paymentService.handleStripeWebhook("payload", "sig");

        assertThat(trial.getStatus()).isEqualTo(TrialStatus.TRIAL_EXPIRED);
        assertThat(trial.getTrialStartDate()).isEqualTo(originalStart);
        assertThat(trial.getTrialExpiryDate()).isEqualTo(originalExpiry);
        verifyNoInteractions(notificationService);
    }

    @Test
    void chargeRefundedEventMarksOrderRefunded() {
        paymentService = service();
        CustomerOrder order = pendingOrder("ORD-6");
        order.setStatus(OrderStatus.PAID);

        Payment originalPayment = new Payment();
        originalPayment.setOrder(order);
        originalPayment.setStripePaymentIntentId("pi_refund_1");
        originalPayment.setStatus(PaymentStatus.PAID);

        Charge charge = new Charge();
        charge.setPaymentIntent("pi_refund_1");
        Event event = mockEvent("evt_4", "charge.refunded", charge);

        when(stripeCheckoutClient.verifyAndParseEvent(anyString(), anyString())).thenReturn(event);
        when(paymentRepository.existsByStripeEventId("evt_4")).thenReturn(false);
        when(paymentRepository.findFirstByStripePaymentIntentIdOrderByCreatedAtDesc("pi_refund_1"))
                .thenReturn(Optional.of(originalPayment));

        paymentService.handleStripeWebhook("payload", "sig");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }
}
