package com.jovens.yoga.service;

import com.jovens.yoga.dto.request.ResendOtpRequest;
import com.jovens.yoga.dto.request.SendOtpRequest;
import com.jovens.yoga.dto.request.VerifyOtpRequest;
import com.jovens.yoga.dto.response.OtpSendResponse;
import com.jovens.yoga.dto.response.OtpVerifyResponse;
import com.jovens.yoga.entity.Otp;
import com.jovens.yoga.enums.OtpPurpose;
import com.jovens.yoga.exception.BusinessException;
import com.jovens.yoga.repository.OtpRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OtpServiceImplTest {

    @Autowired
    private OtpService otpService;

    @Autowired
    private OtpRepository otpRepository;

    private SendOtpRequest sendRequest(String mobile) {
        return new SendOtpRequest("Jane", "otp-" + mobile + "@example.com", "+1", mobile);
    }

    @Test
    void testModeSendReturnsDevOtpAndDoesNotFail() {
        OtpSendResponse response = otpService.sendOtp(sendRequest("7770000001"));

        assertThat(response.devOtp()).isNotBlank();
        assertThat(response.devOtp()).hasSize(4);
        assertThat(response.expiresInSeconds()).isPositive();
    }

    @Test
    void correctCodeVerifiesAndIssuesAOneTimeToken() {
        String mobile = "7770000002";
        OtpSendResponse sent = otpService.sendOtp(sendRequest(mobile));

        OtpVerifyResponse verified = otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, sent.devOtp()));

        assertThat(verified.verified()).isTrue();
        assertThat(verified.verificationToken()).isNotBlank();
    }

    @Test
    void wrongCodeIsRejected() {
        String mobile = "7770000003";
        otpService.sendOtp(sendRequest(mobile));

        assertThatThrownBy(() -> otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, "0000")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid or expired OTP");
    }

    @Test
    void expiredCodeIsRejected() {
        String mobile = "7770000004";
        OtpSendResponse sent = otpService.sendOtp(sendRequest(mobile));

        Otp otp = otpRepository.findByMobileNumberAndPurpose(mobile, OtpPurpose.CHECKOUT).orElseThrow();
        otp.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        otpRepository.saveAndFlush(otp);

        assertThatThrownBy(() -> otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, sent.devOtp())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid or expired OTP");
    }

    @Test
    void maxAttemptsLocksOutFurtherVerification() {
        String mobile = "7770000005";
        otpService.sendOtp(sendRequest(mobile));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, "0000")))
                    .isInstanceOf(BusinessException.class);
        }

        // The 6th attempt (even with correct handling) must be rejected as OTP_MAX_ATTEMPTS,
        // not re-evaluated against the code.
        Otp otp = otpRepository.findByMobileNumberAndPurpose(mobile, OtpPurpose.CHECKOUT).orElseThrow();
        assertThat(otp.getAttempts()).isEqualTo(5);

        assertThatThrownBy(() -> otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, "1234")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Too many incorrect attempts");
    }

    @Test
    void resendInvalidatesThePreviousCode() {
        String mobile = "7770000006";
        OtpSendResponse first = otpService.sendOtp(sendRequest(mobile));

        // Simulate the resend cooldown already having elapsed.
        Otp otp = otpRepository.findByMobileNumberAndPurpose(mobile, OtpPurpose.CHECKOUT).orElseThrow();
        otp.setLastSentAt(LocalDateTime.now().minusMinutes(5));
        otpRepository.saveAndFlush(otp);

        OtpSendResponse second = otpService.resendOtp(new ResendOtpRequest("+1", mobile));
        assertThat(second.devOtp()).isNotEqualTo(first.devOtp());

        assertThatThrownBy(() -> otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, first.devOtp())))
                .isInstanceOf(BusinessException.class);

        OtpVerifyResponse verified = otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, second.devOtp()));
        assertThat(verified.verified()).isTrue();
    }

    @Test
    void resendBeforeCooldownElapsedIsRejected() {
        String mobile = "7770000007";
        otpService.sendOtp(sendRequest(mobile));

        assertThatThrownBy(() -> otpService.resendOtp(new ResendOtpRequest("+1", mobile)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Please wait");
    }

    @Test
    void verificationTokenIsOneTimeUse() {
        String mobile = "7770000008";
        OtpSendResponse sent = otpService.sendOtp(sendRequest(mobile));
        OtpVerifyResponse verified = otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, sent.devOtp()));

        // First consumption succeeds.
        otpService.assertVerifiedAndConsume(verified.verificationToken(), mobile);

        // Replaying the same token must fail — one-time use.
        assertThatThrownBy(() -> otpService.assertVerifiedAndConsume(verified.verificationToken(), mobile))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OTP verification required");
    }

    @Test
    void verificationTokenRejectedForAForeignMobileNumber() {
        String mobile = "7770000009";
        OtpSendResponse sent = otpService.sendOtp(sendRequest(mobile));
        OtpVerifyResponse verified = otpService.verifyOtp(new VerifyOtpRequest("+1", mobile, sent.devOtp()));

        assertThatThrownBy(() -> otpService.assertVerifiedAndConsume(verified.verificationToken(), "9999999999"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("OTP verification required");
    }

    @Test
    void missingOrBlankTokenIsRejected() {
        assertThatThrownBy(() -> otpService.assertVerifiedAndConsume(null, "7770000010"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> otpService.assertVerifiedAndConsume("", "7770000010"))
                .isInstanceOf(BusinessException.class);
    }
}
