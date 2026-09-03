package com.jovens.yoga.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserDetailResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String countryRegion,
        String countryPhoneCode,
        String mobileNumber,
        String address,
        LocalDateTime registeredAt,
        String trialStatus,
        LocalDateTime trialStartDate,
        LocalDateTime trialExpiryDate,
        List<AdminOrderResponse> orders
) {
}
