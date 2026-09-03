package com.jovens.yoga.controller;

import com.jovens.yoga.dto.request.ResendOtpRequest;
import com.jovens.yoga.dto.request.SendOtpRequest;
import com.jovens.yoga.dto.request.VerifyOtpRequest;
import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.dto.response.OtpSendResponse;
import com.jovens.yoga.dto.response.OtpVerifyResponse;
import com.jovens.yoga.service.OtpService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/otp")
public class OtpController {

    private final OtpService otpService;

    public OtpController(OtpService otpService) {
        this.otpService = otpService;
    }

    @PostMapping("/send")
    public ApiResponse<OtpSendResponse> send(@Valid @RequestBody SendOtpRequest request) {
        return ApiResponse.success("OTP sent.", otpService.sendOtp(request));
    }

    @PostMapping("/resend")
    public ApiResponse<OtpSendResponse> resend(@Valid @RequestBody ResendOtpRequest request) {
        return ApiResponse.success("OTP resent.", otpService.resendOtp(request));
    }

    @PostMapping("/verify")
    public ApiResponse<OtpVerifyResponse> verify(@Valid @RequestBody VerifyOtpRequest request) {
        return ApiResponse.success("OTP verified.", otpService.verifyOtp(request));
    }
}
