package com.jovens.yoga.service;

import com.jovens.yoga.entity.Plan;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.entity.User;
import com.jovens.yoga.enums.NotificationType;
import com.jovens.yoga.enums.PlanType;
import com.jovens.yoga.enums.TrialStatus;
import com.jovens.yoga.repository.NotificationRepository;
import com.jovens.yoga.repository.PlanRepository;
import com.jovens.yoga.repository.TrialRepository;
import com.jovens.yoga.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TrialSchedulerServiceTest {

    @Autowired
    private TrialSchedulerService trialSchedulerService;

    @Autowired
    private PlanRepository planRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrialRepository trialRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private User createUser(String email, String mobile) {
        User user = new User();
        user.setFirstName("Jane");
        user.setLastName("Doe");
        user.setEmail(email);
        user.setCountryPhoneCode("+1");
        user.setMobileNumber(mobile);
        return userRepository.save(user);
    }

    private Plan createPlan() {
        Plan plan = new Plan();
        plan.setName("Free Trial");
        plan.setPlanType(PlanType.FREE_TRIAL);
        plan.setCurrency("USD");
        plan.setTrialDurationDays(5);
        plan.setActive(true);
        return planRepository.save(plan);
    }

    @Test
    void expiresOverdueTrialsAndRecordsExpiryNotifications() {
        Plan plan = createPlan();
        User user = createUser("expiring@example.com", "5551110001");

        Trial trial = new Trial();
        trial.setUser(user);
        trial.setPlan(plan);
        trial.setTrialStartDate(LocalDateTime.now().minusDays(10));
        trial.setTrialExpiryDate(LocalDateTime.now().minusDays(5));
        trial.setStatus(TrialStatus.TRIAL_ACTIVE);
        trial = trialRepository.saveAndFlush(trial);

        trialSchedulerService.expireOverdueTrials();

        Trial updated = trialRepository.findById(trial.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TrialStatus.TRIAL_EXPIRED);
        assertThat(notificationRepository.existsByEventKey("TRIAL_EXPIRY_EMAIL_TRIAL_" + trial.getId())).isTrue();
    }

    @Test
    void reminderRunIsIdempotentAcrossRepeatedInvocations() {
        Plan plan = createPlan();
        User user = createUser("reminders@example.com", "5551110002");

        Trial trial = new Trial();
        trial.setUser(user);
        trial.setPlan(plan);
        trial.setTrialStartDate(LocalDateTime.now().minusDays(2));
        trial.setTrialExpiryDate(LocalDateTime.now().plusDays(3));
        trial.setStatus(TrialStatus.TRIAL_ACTIVE);
        Long trialId = trialRepository.saveAndFlush(trial).getId();

        trialSchedulerService.sendDueReminders();
        trialSchedulerService.sendDueReminders();
        trialSchedulerService.sendDueReminders();

        long reminderCount = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.TRIAL_REMINDER)
                .filter(n -> n.getReferenceId().equals(trialId))
                .count();

        // day-1 and day-2 reminders due, EMAIL + WHATSAPP each = 4, regardless of how many times the run fires.
        assertThat(reminderCount).isEqualTo(4);
    }
}
