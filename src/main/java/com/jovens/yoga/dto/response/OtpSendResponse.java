package com.jovens.yoga.dto.response;

/**
 * {@code devOtp} is populated ONLY when {@code app.otp.mode=TEST} — in LIVE mode it is
 * always {@code null}, so the plaintext code is never exposed through a production API
 * response.
 */
public record OtpSendResponse(String message, long expiresInSeconds, String devOtp) {
}
