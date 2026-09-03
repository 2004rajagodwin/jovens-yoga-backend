package com.jovens.yoga.service;

import com.jovens.yoga.dto.request.CreateOrderRequest;
import com.jovens.yoga.dto.request.CreateTrialRequest;
import com.jovens.yoga.dto.request.CustomerDetailsRequest;
import com.jovens.yoga.dto.request.SendOtpRequest;
import com.jovens.yoga.dto.request.VerifyOtpRequest;
import com.jovens.yoga.dto.response.OrderResponse;
import com.jovens.yoga.entity.ClassSlot;
import com.jovens.yoga.entity.Plan;
import com.jovens.yoga.entity.PlanDuration;
import com.jovens.yoga.enums.DurationUnit;
import com.jovens.yoga.enums.PlanType;
import com.jovens.yoga.exception.BusinessException;
import com.jovens.yoga.exception.ResourceNotFoundException;
import com.jovens.yoga.repository.PlanDurationRepository;
import com.jovens.yoga.repository.PlanRepository;
import com.jovens.yoga.repository.SlotRepository;
import com.jovens.yoga.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private TrialService trialService;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private PlanDurationRepository planDurationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private OtpService otpService;

    private ClassSlot createSlot() {
        ClassSlot slot = new ClassSlot();
        slot.setSlotDate(LocalDate.now().plusDays(1));
        slot.setStartTime(LocalTime.of(9, 0));
        slot.setActive(true);
        return slotRepository.save(slot);
    }

    private Plan standardPlan(boolean active) {
        Plan plan = new Plan();
        plan.setName("Standard");
        plan.setPlanType(PlanType.STANDARD);
        plan.setCurrency("USD");
        plan.setActive(active);
        return planRepository.save(plan);
    }

    private PlanDuration duration(Plan plan, BigDecimal price, boolean active) {
        PlanDuration duration = new PlanDuration();
        duration.setPlan(plan);
        duration.setDurationLabel("Per Month");
        duration.setDurationValue(1);
        duration.setDurationUnit(DurationUnit.MONTH);
        duration.setPrice(price);
        duration.setCurrency("USD");
        duration.setActive(active);
        return planDurationRepository.save(duration);
    }

    private CustomerDetailsRequest customer(String email, String mobile) {
        return new CustomerDetailsRequest("Carla", "Diaz", email, "US", "+1", mobile, "5 Elm St");
    }

    /** Runs a real send+verify against the test-mode OtpService and returns a fresh, valid verification token. */
    private String otpToken(String mobile) {
        var sent = otpService.sendOtp(new SendOtpRequest("Carla", "otp-" + mobile + "@example.com", "+1", mobile));
        var verified = otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, sent.devOtp()));
        return verified.verificationToken();
    }

    @Test
    void createsValidPendingOrderWithServerSideAmount() {
        Plan plan = standardPlan(true);
        PlanDuration dur = duration(plan, new BigDecimal("59.00"), true);

        OrderResponse response = orderService.createOrder(
                new CreateOrderRequest(plan.getId(), dur.getId(), customer("carla@example.com", "5552220001"), otpToken("5552220001")));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.amount()).isEqualByComparingTo("59.00");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.planName()).isEqualTo("Standard");
    }

    @Test
    void rejectsOrderForNonExistentPlan() {
        assertThatThrownBy(() -> orderService.createOrder(
                new CreateOrderRequest(999999L, 1L, customer("x@example.com", "5552220002"), otpToken("5552220002"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsOrderForInactivePlan() {
        Plan plan = standardPlan(false);
        PlanDuration dur = duration(plan, new BigDecimal("59.00"), true);

        assertThatThrownBy(() -> orderService.createOrder(
                new CreateOrderRequest(plan.getId(), dur.getId(), customer("y@example.com", "5552220003"), otpToken("5552220003"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsOrderForNonExistentDuration() {
        Plan plan = standardPlan(true);

        assertThatThrownBy(() -> orderService.createOrder(
                new CreateOrderRequest(plan.getId(), 999999L, customer("z@example.com", "5552220004"), otpToken("5552220004"))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsDurationThatBelongsToAnotherPlan() {
        Plan plan = standardPlan(true);
        Plan otherPlan = standardPlan(true);
        PlanDuration foreignDuration = duration(otherPlan, new BigDecimal("99.00"), true);

        assertThatThrownBy(() -> orderService.createOrder(
                new CreateOrderRequest(plan.getId(), foreignDuration.getId(), customer("w@example.com", "5552220005"), otpToken("5552220005"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void existingTrialUserCanPurchaseWithoutCreatingSecondAccount() {
        Plan trialPlan = standardPlan(true);
        PlanDuration trialDuration = duration(trialPlan, new BigDecimal("59.00"), true);
        ClassSlot slot = createSlot();

        String email = "trialuser@example.com";
        String mobile = "5552220006";

        trialService.createTrial(new CreateTrialRequest(trialPlan.getId(), trialDuration.getId(), slot.getId(), customer(email, mobile), otpToken(mobile)));
        long usersAfterTrial = userRepository.count();

        Plan plan = standardPlan(true);
        PlanDuration dur = duration(plan, new BigDecimal("59.00"), true);

        OrderResponse response = orderService.createOrder(
                new CreateOrderRequest(plan.getId(), dur.getId(), customer(email, mobile), otpToken(mobile)));

        assertThat(response.email()).isEqualTo(email);
        assertThat(userRepository.count()).isEqualTo(usersAfterTrial);
    }

    @Test
    void historicalOrderAmountIsUnaffectedByLaterPlanPriceChange() {
        Plan plan = standardPlan(true);
        PlanDuration dur = duration(plan, new BigDecimal("59.00"), true);

        OrderResponse original = orderService.createOrder(
                new CreateOrderRequest(plan.getId(), dur.getId(), customer("historical@example.com", "5552220007"), otpToken("5552220007")));

        dur.setPrice(new BigDecimal("129.00"));
        planDurationRepository.save(dur);

        OrderResponse reFetched = orderService.getOrderStatus(original.orderNumber());
        assertThat(reFetched.amount()).isEqualByComparingTo("59.00");
    }
}
