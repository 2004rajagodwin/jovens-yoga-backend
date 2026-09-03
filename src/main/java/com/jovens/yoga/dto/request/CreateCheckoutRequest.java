package com.jovens.yoga.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateCheckoutRequest(@NotBlank String orderNumber) {
}
