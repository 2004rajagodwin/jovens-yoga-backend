package com.jovens.yoga.mapper;

import com.jovens.yoga.dto.response.TrialResponse;
import com.jovens.yoga.entity.ClassSlot;
import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.entity.PlanDuration;
import com.jovens.yoga.entity.Trial;
import org.springframework.stereotype.Component;

@Component
public class TrialMapper {

    public TrialResponse toResponse(Trial trial) {
        return toResponse(trial, null, null);
    }

    /**
     * @param latestPayment the trial's most recent PAID post-trial invoice charge, if any —
     *                      lets the frontend distinguish "still on trial" from "converted to
     *                      paid" for the same TRIAL_EXPIRED status without a new enum value.
     */
    public TrialResponse toResponse(Trial trial, Payment latestPayment) {
        return toResponse(trial, latestPayment, null);
    }

    /**
     * @param freshAccessToken pass the raw token ONLY immediately after issuing it (trial
     *                         creation) — never re-derive or echo it on any other read.
     */
    public TrialResponse toResponse(Trial trial, Payment latestPayment, String freshAccessToken) {
        PlanDuration duration = trial.getPlanDuration();
        ClassSlot slot = trial.getSelectedSlot();

        return new TrialResponse(
                trial.getId(),
                trial.getStatus().name(),
                trial.getTrialStartDate(),
                trial.getTrialExpiryDate(),
                trial.getPlan().getId(),
                trial.getPlan().getName(),
                trial.getUser().getId(),
                trial.getUser().getFirstName(),
                trial.getUser().getLastName(),
                trial.getUser().getEmail(),
                duration != null ? duration.getId() : null,
                duration != null ? duration.getDurationLabel() : null,
                duration != null ? duration.getPrice() : null,
                duration != null ? duration.getCurrency() : null,
                slot != null ? slot.getId() : null,
                slot != null ? slot.getLabel() : null,
                slot != null ? slot.getSlotDate() : null,
                slot != null ? slot.getStartTime() : null,
                trial.getStripeSubscriptionId(),
                latestPayment != null ? latestPayment.getAmount() : null,
                latestPayment != null ? latestPayment.getCurrency() : null,
                latestPayment != null ? latestPayment.getPaidAt() : null,
                latestPayment != null ? latestPayment.getStripeEventId() : null,
                freshAccessToken
        );
    }
}
