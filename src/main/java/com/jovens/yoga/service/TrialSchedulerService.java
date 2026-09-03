package com.jovens.yoga.service;

public interface TrialSchedulerService {

    /** Sends any due day-N trial reminders that have not already been sent. */
    void sendDueReminders();

    /** Transitions any TRIAL_ACTIVE trials past their expiry date to TRIAL_EXPIRED and notifies. */
    void expireOverdueTrials();
}
