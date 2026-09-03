package com.jovens.yoga.dto.response;

/**
 * {@code verificationToken} is a short-lived, one-time-use proof of OTP success — required
 * by {@code POST /api/trials} and {@code POST /api/orders} as {@code otpToken}. It is
 * unrelated to, and does not replace, the existing trial access-token mechanism.
 */
public record OtpVerifyResponse(boolean verified, String verificationToken, String message) {
}
