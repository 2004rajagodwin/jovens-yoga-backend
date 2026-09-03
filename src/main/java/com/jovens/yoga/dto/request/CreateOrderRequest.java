package com.jovens.yoga.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull Long planId,
        @NotNull Long durationId,
        @Valid @NotNull CustomerDetailsRequest customer,
        @NotBlank String otpToken
) {
}
