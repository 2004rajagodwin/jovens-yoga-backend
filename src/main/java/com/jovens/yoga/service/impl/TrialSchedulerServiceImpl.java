package com.jovens.yoga.service.impl;

import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.enums.TrialStatus;
import com.jovens.yoga.repository.TrialRepository;
import com.jovens.yoga.service.NotificationService;
import com.jovens.yoga.service.TrialSchedulerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

@Service
public class TrialSchedulerServiceImpl implements TrialSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(TrialSchedulerServiceImpl.class);

    private final TrialRepository trialRepository;
    private final NotificationService notificationService;
    private final List<Integer> reminderDays;

    public TrialSchedulerServiceImpl(TrialRepository trialRepository,
                                      NotificationService notificationService,
                                      @Value("${app.trial.reminder-days}") String reminderDaysConfig) {
        this.trialRepository = trialRepository;
        this.notificationService = notificationService;
        this.reminderDays = Arrays.stream(reminderDaysConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .sorted()
                .toList();
    }

    @Override
    @Transactional
    public void sendDueReminders() {
        List<Trial> activeTrials = trialRepository.findByStatus(TrialStatus.TRIAL_ACTIVE);
        LocalDate today = LocalDate.now();

        for (Trial trial : activeTrials) {
            long daysElapsed = ChronoUnit.DAYS.between(trial.getTrialStartDate().toLocalDate(), today);

            for (int day : reminderDays) {
                if (day <= daysElapsed && trial.getLastReminderDayIndex() < day) {
                    notificationService.recordTrialReminder(trial.getUser(), trial, day);
                    trial.setLastReminderDayIndex(day);
                }
            }
        }
    }

    @Override
    @Transactional
    public void expireOverdueTrials() {
        List<Trial> overdue = trialRepository.findByStatusAndTrialExpiryDateBefore(TrialStatus.TRIAL_ACTIVE, LocalDateTime.now());

        for (Trial trial : overdue) {
            trial.setStatus(TrialStatus.TRIAL_EXPIRED);
            notificationService.recordTrialExpiry(trial.getUser(), trial);
            log.info("Trial {} expired for user {}", trial.getId(), trial.getUser().getId());
        }
    }
}
