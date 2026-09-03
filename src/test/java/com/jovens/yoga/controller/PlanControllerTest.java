package com.jovens.yoga.controller;

import com.jovens.yoga.entity.Plan;
import com.jovens.yoga.entity.PlanDuration;
import com.jovens.yoga.entity.PlanFeature;
import com.jovens.yoga.enums.DurationUnit;
import com.jovens.yoga.enums.PlanType;
import com.jovens.yoga.repository.PlanRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlanRepository planRepository;

    @Test
    void activePlansEndpointIsPublicAndReturnsOnlyActivePlans() throws Exception {
        Plan active = buildPlan("Standard", PlanType.STANDARD, true, 0);
        Plan inactive = buildPlan("Retired Plan", PlanType.STANDARD, false, 1);
        planRepository.save(active);
        planRepository.save(inactive);

        mockMvc.perform(get("/api/plans/active"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Standard")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Retired Plan"))));
    }

    private Plan buildPlan(String name, PlanType type, boolean active, int order) {
        Plan plan = new Plan();
        plan.setName(name);
        plan.setPlanType(type);
        plan.setCurrency("USD");
        plan.setActive(active);
        plan.setDisplayOrder(order);

        PlanDuration duration = new PlanDuration();
        duration.setPlan(plan);
        duration.setDurationLabel("Per Month");
        duration.setDurationValue(1);
        duration.setDurationUnit(DurationUnit.MONTH);
        duration.setPrice(new BigDecimal("29.00"));
        duration.setCurrency("USD");
        plan.getDurations().add(duration);

        PlanFeature feature = new PlanFeature();
        feature.setPlan(plan);
        feature.setFeatureText("Unlimited classes");
        plan.getFeatures().add(feature);

        return plan;
    }
}
