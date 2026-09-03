package com.jovens.yoga.service;

import com.jovens.yoga.dto.request.CreateTrialRequest;
import com.jovens.yoga.dto.request.CustomerDetailsRequest;
import com.jovens.yoga.dto.request.SendOtpRequest;
import com.jovens.yoga.dto.request.TrialEligibilityRequest;
import com.jovens.yoga.dto.request.VerifyOtpRequest;
import com.jovens.yoga.dto.response.TrialEligibilityResponse;
import com.jovens.yoga.dto.response.TrialResponse;
import com.jovens.yoga.entity.ClassSlot;
import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.entity.Plan;
import com.jovens.yoga.entity.PlanDuration;
import com.jovens.yoga.enums.DurationUnit;
import com.jovens.yoga.enums.PaymentStatus;
import com.jovens.yoga.enums.PlanType;
import com.jovens.yoga.exception.TrialAlreadyUsedException;
import com.jovens.yoga.repository.PaymentRepository;
import com.jovens.yoga.repository.PlanDurationRepository;
import com.jovens.yoga.repository.PlanRepository;
import com.jovens.yoga.repository.SlotRepository;
import com.jovens.yoga.repository.TrialRepository;
import com.jovens.yoga.repository.UserRepository;
import com.jovens.yoga.service.OtpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class TrialServiceTest {

    @Autowired
    private TrialService trialService;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanDurationRepository planDurationRepository;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrialRepository trialRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OtpService otpService;

    private Plan createStandardPlan() {
        Plan plan = new Plan();
        plan.setName("Standard");
        plan.setPlanType(PlanType.STANDARD);
        plan.setCurrency("USD");
        plan.setTrialDurationDays(5);
        plan.setActive(true);
        return planRepository.save(plan);
    }

    private PlanDuration createDuration(Plan plan) {
        PlanDuration duration = new PlanDuration();
        duration.setPlan(plan);
        duration.setDurationLabel("Per Month");
        duration.setDurationValue(1);
        duration.setDurationUnit(DurationUnit.MONTH);
        duration.setPrice(new BigDecimal("59.00"));
        duration.setCurrency("USD");
        duration.setActive(true);
        return planDurationRepository.save(duration);
    }

    private ClassSlot createSlot() {
        ClassSlot slot = new ClassSlot();
        slot.setSlotDate(LocalDate.now().plusDays(1));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setActive(true);
        return slotRepository.save(slot);
    }

    private CustomerDetailsRequest customer(String email, String mobile) {
        return new CustomerDetailsRequest("Jane", "Doe", email, "US", "+1", mobile, "123 Main St");
    }

    /** Runs a real send+verify against the test-mode OtpService and returns a fresh, valid verification token. */
    private String otpToken(String mobile) {
        var sent = otpService.sendOtp(new SendOtpRequest("Jane", "otp-" + mobile + "@example.com", "+1", mobile));
        var verified = otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, sent.devOtp()));
        return verified.verificationToken();
    }

    @Test
    void newCustomerIsEligibleAndTrialIsPendingPayment() {
        Plan plan = createStandardPlan();
        PlanDuration duration = createDuration(plan);
        ClassSlot slot = createSlot();

        TrialEligibilityResponse eligibility = trialService.checkEligibility(
                new TrialEligibilityRequest("newuser@example.com", "5551234567"));
        assertThat(eligibility.eligible()).isTrue();

        TrialResponse response = trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slot.getId(), customer("newuser@example.com", "5551234567"), otpToken("5551234567")));

        assertThat(response.status()).isEqualTo("TRIAL_PENDING_PAYMENT");
        assertThat(response.email()).isEqualTo("newuser@example.com");
        assertThat(response.trialStartDate()).isNull();
        assertThat(userRepository.existsByEmailIgnoreCase("newuser@example.com")).isTrue();
    }

    @Test
    void duplicatePendingTrialForSameUserReusesTheSameRow() {
        Plan plan = createStandardPlan();
        PlanDuration duration = createDuration(plan);
        ClassSlot slot = createSlot();

        TrialResponse first = trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slot.getId(), customer("pending@example.com", "5559990003"), otpToken("5559990003")));

        TrialResponse second = trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slot.getId(), customer("pending@example.com", "5559990003"), otpToken("5559990003")));

        assertThat(second.id()).isEqualTo(first.id());
        // Scoped to this test's own user — trialRepository.count() is global and flaky here
        // since this test class shares one DB context across tests without transactional
        // rollback, so other tests' trial rows are also present at this point.
        assertThat(trialRepository.findByUserId(first.userId())).isPresent();
    }

    @Test
    void trialCanBeCancelledWhilePendingPayment() {
        Plan plan = createStandardPlan();
        PlanDuration duration = createDuration(plan);
        ClassSlot slot = createSlot();

        TrialResponse created = trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slot.getId(), customer("cancel@example.com", "5559990004"), otpToken("5559990004")));

        trialService.cancelTrial(created.id());

        TrialResponse fetched = trialService.getTrial(created.id());
        assertThat(fetched.status()).isEqualTo("CANCELLED");
    }

    @Test
    void activeOrExpiredTrialBlocksClaimingASecondTrial() {
        Plan plan = createStandardPlan();
        PlanDuration duration = createDuration(plan);
        ClassSlot slot = createSlot();

        TrialResponse created = trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slot.getId(), customer("active@example.com", "5559990005"), otpToken("5559990005")));

        // Simulate the webhook activating the trial (normally done by PaymentServiceImpl).
        var trial = trialRepository.findById(created.id()).orElseThrow();
        trial.setStatus(com.jovens.yoga.enums.TrialStatus.TRIAL_ACTIVE);
        trial.setTrialStartDate(java.time.LocalDateTime.now());
        trial.setTrialExpiryDate(java.time.LocalDateTime.now().plusDays(5));
        trialRepository.saveAndFlush(trial);

        TrialEligibilityResponse eligibility = trialService.checkEligibility(
                new TrialEligibilityRequest("active@example.com", "5559990006"));
        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.status()).isEqualTo("TRIAL_ACTIVE");
        assertThat(eligibility.accessToken()).isNotBlank();
        // The token must resolve to this same trial, never expose or equal the raw numeric id.
        assertThat(eligibility.accessToken()).isNotEqualTo(String.valueOf(created.id()));
        TrialResponse viaToken = trialService.getTrialByAccessToken(eligibility.accessToken());
        assertThat(viaToken.id()).isEqualTo(created.id());
        assertThat(eligibility.message()).contains("already active");

        assertThatThrownBy(() -> trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slot.getId(), customer("active@example.com", "5559990006"), otpToken("5559990006"))))
                .isInstanceOf(TrialAlreadyUsedException.class);
    }

    @Test
    void expiredTrialIsDistinguishedFromActiveTrialInEligibilityResponse() {
        Plan plan = createStandardPlan();
        PlanDuration duration = createDuration(plan);
        ClassSlot slot = createSlot();

        TrialResponse created = trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slot.getId(), customer("expired@example.com", "5559990007"), otpToken("5559990007")));

        var trial = trialRepository.findById(created.id()).orElseThrow();
        trial.setStatus(com.jovens.yoga.enums.TrialStatus.TRIAL_EXPIRED);
        trial.setTrialStartDate(java.time.LocalDateTime.now().minusDays(10));
        trial.setTrialExpiryDate(java.time.LocalDateTime.now().minusDays(5));
        trialRepository.saveAndFlush(trial);

        TrialEligibilityResponse eligibility = trialService.checkEligibility(
                new TrialEligibilityRequest("expired@example.com", "5559990007"));
        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.status()).isEqualTo("TRIAL_EXPIRED");
        assertThat(eligibility.accessToken()).isNotBlank();
        TrialResponse viaToken = trialService.getTrialByAccessToken(eligibility.accessToken());
        assertThat(viaToken.id()).isEqualTo(created.id());
        assertThat(eligibility.message()).contains("already been used");
    }

    @Test
    void convertedTrialReportsActiveNotExpiredSoUserIsNeverAskedToPayAgain() {
        Plan plan = createStandardPlan();
        PlanDuration duration = createDuration(plan);
        ClassSlot slot = createSlot();

        TrialResponse created = trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slot.getId(), customer("converted@example.com", "5559990008"), otpToken("5559990008")));

        var trial = trialRepository.findById(created.id()).orElseThrow();
        trial.setStatus(com.jovens.yoga.enums.TrialStatus.TRIAL_EXPIRED);
        trial.setTrialStartDate(java.time.LocalDateTime.now().minusDays(10));
        trial.setTrialExpiryDate(java.time.LocalDateTime.now().minusDays(5));
        trialRepository.saveAndFlush(trial);

        // Simulate a successful post-trial auto-charge (normally recorded by PaymentServiceImpl
        // when invoice.payment_succeeded arrives).
        Payment payment = new Payment();
        payment.setTrial(trial);
        payment.setStripeEventId("evt_test_converted");
        payment.setAmount(duration.getPrice());
        payment.setCurrency(duration.getCurrency());
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(java.time.LocalDateTime.now().minusDays(5));
        paymentRepository.saveAndFlush(payment);

        TrialEligibilityResponse eligibility = trialService.checkEligibility(
                new TrialEligibilityRequest("converted@example.com", "5559990008"));
        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.status()).isEqualTo("ACTIVE");
        assertThat(eligibility.message()).contains("already have an active paid membership");
    }

    @Test
    void trialCanBeRetrievedByIdForThankYouPage() {
        Plan plan = createStandardPlan();
        PlanDuration duration = createDuration(plan);
        ClassSlot slot = createSlot();

        TrialResponse created = trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slot.getId(), customer("thankyou@example.com", "5557770001"), otpToken("5557770001")));

        TrialResponse fetched = trialService.getTrial(created.id());

        assertThat(fetched.status()).isEqualTo("TRIAL_PENDING_PAYMENT");
        assertThat(fetched.planName()).isEqualTo("Standard");
        assertThat(fetched.firstName()).isEqualTo("Jane");
        assertThat(fetched.durationLabel()).isEqualTo("Per Month");
        assertThat(fetched.slotId()).isEqualTo(slot.getId());
    }

    // ---------------------------------------------------------------------------------------
    // IDOR fix: GET /api/trials/{id} no longer exists publicly. Everything below tests the
    // token-based replacement that closes it.
    // ---------------------------------------------------------------------------------------

    @Test
    void validAccessTokenReturnsOnlyItsOwnTrial() {
        Plan plan = createStandardPlan();
        PlanDuration duration = createDuration(plan);
        ClassSlot slot = createSlot();

        TrialEligibilityResponse userAEligibility = trialService.checkEligibility(
                new TrialEligibilityRequest("usera@example.com", "5558880001"));
        assertThat(userAEligibility.eligible()).isTrue();

        TrialResponse userATrial = trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slot.getId(), customer("usera@example.com", "5558880001"), otpToken("5558880001")));
        var trial = trialRepository.findById(userATrial.id()).orElseThrow();
        trial.setStatus(com.jovens.yoga.enums.TrialStatus.TRIAL_ACTIVE);
        trial.setTrialStartDate(java.time.LocalDateTime.now());
        trial.setTrialExpiryDate(java.time.LocalDateTime.now().plusDays(5));
        trialRepository.saveAndFlush(trial);

        TrialEligibilityResponse eligibilityA = trialService.checkEligibility(
                new TrialEligibilityRequest("usera@example.com", "5558880001"));
        String tokenA = eligibilityA.accessToken();

        TrialResponse viaToken = trialService.getTrialByAccessToken(tokenA);
        assertThat(viaToken.id()).isEqualTo(userATrial.id());
        assertThat(viaToken.email()).isEqualTo("usera@example.com");
    }

    @Test
    void accessTokenForOneTrialCannotBeUsedToReadAnotherUsersTrial() {
        Plan plan = createStandardPlan();
        PlanDuration duration = createDuration(plan);
        ClassSlot slotA = createSlot();
        ClassSlot slotB = createSlot();

        TrialResponse trialA = trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slotA.getId(), customer("userb1@example.com", "5558880002"), otpToken("5558880002")));
        var entityA = trialRepository.findById(trialA.id()).orElseThrow();
        entityA.setStatus(com.jovens.yoga.enums.TrialStatus.TRIAL_ACTIVE);
        entityA.setTrialStartDate(java.time.LocalDateTime.now());
        entityA.setTrialExpiryDate(java.time.LocalDateTime.now().plusDays(5));
        trialRepository.saveAndFlush(entityA);

        TrialResponse trialB = trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slotB.getId(), customer("userb2@example.com", "5558880003"), otpToken("5558880003")));
        var entityB = trialRepository.findById(trialB.id()).orElseThrow();
        entityB.setStatus(com.jovens.yoga.enums.TrialStatus.TRIAL_ACTIVE);
        entityB.setTrialStartDate(java.time.LocalDateTime.now());
        entityB.setTrialExpiryDate(java.time.LocalDateTime.now().plusDays(5));
        trialRepository.saveAndFlush(entityB);

        String tokenA = trialService.checkEligibility(
                new TrialEligibilityRequest("userb1@example.com", "5558880002")).accessToken();
        String tokenB = trialService.checkEligibility(
                new TrialEligibilityRequest("userb2@example.com", "5558880003")).accessToken();

        assertThat(trialService.getTrialByAccessToken(tokenA).id()).isEqualTo(trialA.id());
        assertThat(trialService.getTrialByAccessToken(tokenB).id()).isEqualTo(trialB.id());
        // The critical assertion: token A must never resolve to trial B, or vice versa.
        assertThat(trialService.getTrialByAccessToken(tokenA).id()).isNotEqualTo(trialB.id());
    }

    @Test
    void randomOrForgedAccessTokenIsRejected() {
        assertThatThrownBy(() -> trialService.getTrialByAccessToken("this-token-was-never-issued-by-the-server"))
                .isInstanceOf(com.jovens.yoga.exception.ResourceNotFoundException.class);
        assertThatThrownBy(() -> trialService.getTrialByAccessToken(""))
                .isInstanceOf(com.jovens.yoga.exception.ResourceNotFoundException.class);
        assertThatThrownBy(() -> trialService.getTrialByAccessToken(null))
                .isInstanceOf(com.jovens.yoga.exception.ResourceNotFoundException.class);
    }

    @Test
    void expiredAccessTokenIsRejectedEvenIfOtherwiseValid() {
        Plan plan = createStandardPlan();
        PlanDuration duration = createDuration(plan);
        ClassSlot slot = createSlot();

        TrialResponse created = trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slot.getId(), customer("expiredtoken@example.com", "5558880004"), otpToken("5558880004")));
        var trial = trialRepository.findById(created.id()).orElseThrow();
        trial.setStatus(com.jovens.yoga.enums.TrialStatus.TRIAL_ACTIVE);
        trial.setTrialStartDate(java.time.LocalDateTime.now());
        trial.setTrialExpiryDate(java.time.LocalDateTime.now().plusDays(5));
        trialRepository.saveAndFlush(trial);

        String token = trialService.checkEligibility(
                new TrialEligibilityRequest("expiredtoken@example.com", "5558880004")).accessToken();

        // Simulate the token having expired by directly backdating its expiry (same effect as
        // waiting out the real 30-minute TTL, without actually waiting in the test).
        var refreshed = trialRepository.findById(created.id()).orElseThrow();
        refreshed.setAccessTokenExpiresAt(java.time.LocalDateTime.now().minusMinutes(1));
        trialRepository.saveAndFlush(refreshed);

        assertThatThrownBy(() -> trialService.getTrialByAccessToken(token))
                .isInstanceOf(com.jovens.yoga.exception.ResourceNotFoundException.class);
    }

    @Test
    void accessTokenIsReadOnlyItCannotBeUsedToDoAnythingButReadOrCancelAPendingTrial() {
        // cancelTrialByAccessToken only ever downgrades a TRIAL_PENDING_PAYMENT row (same
        // safety rule as the internal cancelTrial(id)) — it must never touch an ACTIVE trial.
        Plan plan = createStandardPlan();
        PlanDuration duration = createDuration(plan);
        ClassSlot slot = createSlot();

        TrialResponse created = trialService.createTrial(
                new CreateTrialRequest(plan.getId(), duration.getId(), slot.getId(), customer("activenocancel@example.com", "5558880005"), otpToken("5558880005")));
        var trial = trialRepository.findById(created.id()).orElseThrow();
        trial.setStatus(com.jovens.yoga.enums.TrialStatus.TRIAL_ACTIVE);
        trial.setTrialStartDate(java.time.LocalDateTime.now());
        trial.setTrialExpiryDate(java.time.LocalDateTime.now().plusDays(5));
        trialRepository.saveAndFlush(trial);

        String token = trialService.checkEligibility(
                new TrialEligibilityRequest("activenocancel@example.com", "5558880005")).accessToken();

        trialService.cancelTrialByAccessToken(token);

        TrialResponse stillActive = trialService.getTrialByAccessToken(token);
        assertThat(stillActive.status()).isEqualTo("TRIAL_ACTIVE");
    }
}
