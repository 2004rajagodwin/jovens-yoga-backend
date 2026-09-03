package com.jovens.yoga.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{1,5}$", message = "Invalid country phone code") String countryPhoneCode,
        @NotBlank @Pattern(regexp = "^[0-9]{4,15}$", message = "Invalid mobile number") String mobileNumber,
        @NotBlank @Pattern(regexp = "^[0-9]{4,8}$", message = "Invalid OTP code") String code
) {
}
