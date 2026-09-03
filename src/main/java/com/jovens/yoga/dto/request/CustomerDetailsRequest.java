package com.jovens.yoga.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerDetailsRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 100) String countryRegion,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{1,5}$", message = "Invalid country phone code") String countryPhoneCode,
        @NotBlank @Pattern(regexp = "^[0-9]{4,15}$", message = "Invalid mobile number") String mobileNumber,
        @Size(max = 500) String address
) {
}
