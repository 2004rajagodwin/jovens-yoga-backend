package com.jovens.yoga.service;

import com.jovens.yoga.dto.request.AdminPlanDurationRequest;
import com.jovens.yoga.dto.request.AdminPlanFeatureRequest;
import com.jovens.yoga.dto.request.AdminPlanRequest;
import com.jovens.yoga.dto.request.CreateOrderRequest;
import com.jovens.yoga.dto.request.CustomerDetailsRequest;
import com.jovens.yoga.dto.request.SendOtpRequest;
import com.jovens.yoga.dto.request.VerifyOtpRequest;
import com.jovens.yoga.dto.response.OrderResponse;
import com.jovens.yoga.dto.response.PlanResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PlanServiceImplTest {

    @Autowired
    private PlanService planService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private OtpService otpService;

    private String otpToken(String mobile) {
        var sent = otpService.sendOtp(new SendOtpRequest("Pat", "otp-" + mobile + "@example.com", "+1", mobile));
        var verified = otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, sent.devOtp()));
        return verified.verificationToken();
    }

    private AdminPlanRequest planRequest(BigDecimal price, List<AdminPlanDurationRequest> durations) {
        return new AdminPlanRequest(
                "Premium", "Complete experience", null, "PREMIUM", "USD", null,
                true, "Best choice", true, 0,
                durations,
                List.of(new AdminPlanFeatureRequest(null, "Live classes", 0, true))
        );
    }

    @Test
    void updatingPlanPriceDoesNotBreakWhenDurationIsReferencedByAnExistingOrder() {
        // Create the plan with one duration.
        PlanResponse created = planService.createPlan(planRequest(new BigDecimal("59.00"),
                List.of(new AdminPlanDurationRequest(null, "Per Month", 1, "MONTH", new BigDecimal("59.00"), "USD", 0, true))));

        Long durationId = created.durations().get(0).id();

        // Place a real order against that duration — this creates a NOT NULL FK from
        // orders.plan_duration_id to this exact plan_durations row.
        CustomerDetailsRequest customer = new CustomerDetailsRequest(
                "Pat", "Buyer", "pat.buyer@example.com", "US", "+1", "5550001111", "1 Main St");
        OrderResponse order = orderService.createOrder(new CreateOrderRequest(created.id(), durationId, customer, otpToken("5550001111")));
        assertThat(order.amount()).isEqualByComparingTo("59.00");

        // Admin updates the plan, changing the price of that SAME duration (by id).
        PlanResponse updated = planService.updatePlan(created.id(), planRequest(new BigDecimal("99.99"),
                List.of(new AdminPlanDurationRequest(durationId, "Per Month", 1, "MONTH", new BigDecimal("99.99"), "USD", 0, true))));

        assertThat(updated.durations()).hasSize(1);
        assertThat(updated.durations().get(0).id()).isEqualTo(durationId);
        assertThat(updated.durations().get(0).price()).isEqualByComparingTo("99.99");

        // The historical order must be completely unaffected by the price change.
        OrderResponse reFetched = orderService.getOrderStatus(order.orderNumber());
        assertThat(reFetched.amount()).isEqualByComparingTo("59.00");

        // The public/active plan listing must show the new price.
        List<PlanResponse> activePlans = planService.getActivePlans();
        PlanResponse premium = activePlans.stream().filter(p -> p.id().equals(created.id())).findFirst().orElseThrow();
        assertThat(premium.durations().get(0).price()).isEqualByComparingTo("99.99");
    }

    @Test
    void addingAndRemovingDurationsOnUpdateWorksAlongsideAnUnchangedOne() {
        PlanResponse created = planService.createPlan(planRequest(new BigDecimal("29.00"),
                List.of(
                        new AdminPlanDurationRequest(null, "Per Month", 1, "MONTH", new BigDecimal("29.00"), "USD", 0, true),
                        new AdminPlanDurationRequest(null, "Per Year", 12, "MONTH", new BigDecimal("290.00"), "USD", 1, true)
                )));

        Long keepId = created.durations().get(0).id();

        // Update: keep the first duration (by id), drop the second, add a brand new third.
        PlanResponse updated = planService.updatePlan(created.id(), planRequest(new BigDecimal("29.00"), List.of(
                new AdminPlanDurationRequest(keepId, "Per Month", 1, "MONTH", new BigDecimal("29.00"), "USD", 0, true),
                new AdminPlanDurationRequest(null, "Per 6 Months", 6, "MONTH", new BigDecimal("150.00"), "USD", 1, true)
        )));

        assertThat(updated.durations()).hasSize(2);
        assertThat(updated.durations().stream().map(d -> d.id())).contains(keepId);
    }

}
