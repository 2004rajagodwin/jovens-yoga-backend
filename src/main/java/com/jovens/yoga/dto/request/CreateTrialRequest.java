package com.jovens.yoga.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTrialRequest(
        @NotNull Long planId,
        @NotNull Long planDurationId,
        @NotNull Long slotId,
        @Valid @NotNull CustomerDetailsRequest customer,
        @NotBlank String otpToken
) {
}
