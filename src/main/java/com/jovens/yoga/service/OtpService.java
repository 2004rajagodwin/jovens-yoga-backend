package com.jovens.yoga.service;

import com.jovens.yoga.dto.request.ResendOtpRequest;
import com.jovens.yoga.dto.request.SendOtpRequest;
import com.jovens.yoga.dto.request.VerifyOtpRequest;
import com.jovens.yoga.dto.response.OtpSendResponse;
import com.jovens.yoga.dto.response.OtpVerifyResponse;

public interface OtpService {

    OtpSendResponse sendOtp(SendOtpRequest request);

    OtpSendResponse resendOtp(ResendOtpRequest request);

    OtpVerifyResponse verifyOtp(VerifyOtpRequest request);

    /**
     * Validates a raw verification token (issued by {@link #verifyOtp}) against the given
     * mobile number and consumes it (one-time use). Throws {@link com.jovens.yoga.exception.BusinessException}
     * on any missing/expired/foreign-phone/already-consumed token — the same message either
     * way, never revealing which case it was.
     */
    void assertVerifiedAndConsume(String rawVerificationToken, String mobileNumber);
}
