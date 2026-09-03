package com.jovens.yoga.dto.response;

import java.time.LocalDateTime;

public record AdminUserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String countryRegion,
        String countryPhoneCode,
        String mobileNumber,
        LocalDateTime registeredAt,
        String trialStatus,
        String currentPlan
) {
}
