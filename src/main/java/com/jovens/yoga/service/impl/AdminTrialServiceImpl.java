package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.response.AdminTrialResponse;
import com.jovens.yoga.dto.response.PageResponse;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.enums.TrialStatus;
import com.jovens.yoga.exception.ResourceNotFoundException;
import com.jovens.yoga.repository.TrialRepository;
import com.jovens.yoga.service.AdminTrialService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminTrialServiceImpl implements AdminTrialService {

    private final TrialRepository trialRepository;

    public AdminTrialServiceImpl(TrialRepository trialRepository) {
        this.trialRepository = trialRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminTrialResponse> listTrials(TrialStatus status, String search, Pageable pageable) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search;
        return PageResponse.from(trialRepository.search(status, normalizedSearch, pageable).map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminTrialResponse getTrial(Long id) {
        Trial trial = trialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trial not found with id: " + id));
        return toResponse(trial);
    }

    private AdminTrialResponse toResponse(Trial trial) {
        return new AdminTrialResponse(
                trial.getId(),
                trial.getUser().getFirstName() + " " + trial.getUser().getLastName(),
                trial.getUser().getEmail(),
                trial.getUser().getMobileNumber(),
                trial.getPlan().getName(),
                trial.getTrialStartDate(),
                trial.getTrialExpiryDate(),
                trial.getStatus().name(),
                trial.getLastReminderDayIndex(),
                trial.getCreatedAt(),
                trial.getPlanDuration() != null ? trial.getPlanDuration().getDurationLabel() : null,
                trial.getSelectedSlot() != null ? trial.getSelectedSlot().getLabel() : null,
                trial.getSelectedSlot() != null ? trial.getSelectedSlot().getSlotDate() : null,
                trial.getStripeSubscriptionId(),
                trial.getStripeCustomerId()
        );
    }
}
