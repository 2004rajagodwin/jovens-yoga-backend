package com.jovens.yoga.exception;

import org.springframework.http.HttpStatus;

public class TrialAlreadyUsedException extends BusinessException {
    public TrialAlreadyUsedException() {
        super("Your free trial has already been used. Please choose a Standard or Premium plan.",
                "TRIAL_ALREADY_USED", HttpStatus.CONFLICT);
    }
}
