package com.jovens.yoga.scheduler;

import com.jovens.yoga.service.TrialSchedulerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TrialReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrialReminderScheduler.class);

    private final TrialSchedulerService trialSchedulerService;

    public TrialReminderScheduler(TrialSchedulerService trialSchedulerService) {
        this.trialSchedulerService = trialSchedulerService;
    }

    @Scheduled(cron = "${app.trial.reminder-cron:0 0 * * * *}")
    public void sendDueReminders() {
        try {
            trialSchedulerService.sendDueReminders();
        } catch (Exception ex) {
            log.error("Trial reminder run failed", ex);
        }
    }

    @Scheduled(cron = "${app.trial.expiry-cron:0 15 * * * *}")
    public void expireOverdueTrials() {
        try {
            trialSchedulerService.expireOverdueTrials();
        } catch (Exception ex) {
            log.error("Trial expiry run failed", ex);
        }
    }
}
