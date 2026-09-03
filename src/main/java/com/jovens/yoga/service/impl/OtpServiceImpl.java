package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.request.ResendOtpRequest;
import com.jovens.yoga.dto.request.SendOtpRequest;
import com.jovens.yoga.dto.request.VerifyOtpRequest;
import com.jovens.yoga.dto.response.OtpSendResponse;
import com.jovens.yoga.dto.response.OtpVerifyResponse;
import com.jovens.yoga.entity.Otp;
import com.jovens.yoga.enums.OtpPurpose;
import com.jovens.yoga.enums.OtpStatus;
import com.jovens.yoga.exception.BusinessException;
import com.jovens.yoga.exception.WhatsAppDeliveryException;
import com.jovens.yoga.repository.OtpRepository;
import com.jovens.yoga.security.AccessTokenGenerator;
import com.jovens.yoga.service.OtpService;
import com.jovens.yoga.service.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class OtpServiceImpl implements OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);

    private static final String INVALID_MESSAGE = "Invalid or expired OTP.";
    private static final OtpPurpose PURPOSE = OtpPurpose.CHECKOUT;

    private final OtpRepository otpRepository;
    private final WhatsAppService whatsAppService;
    private final AccessTokenGenerator accessTokenGenerator;
    private final String mode;
    private final int otpLength;
    private final long expiryMinutes;
    private final int maxAttempts;
    private final long resendCooldownSeconds;
    private final long verificationTokenTtlMinutes;

    public OtpServiceImpl(OtpRepository otpRepository,
                           WhatsAppService whatsAppService,
                           AccessTokenGenerator accessTokenGenerator,
                           @Value("${app.otp.mode}") String mode,
                           @Value("${app.otp.length}") int otpLength,
                           @Value("${app.otp.expiry-minutes}") long expiryMinutes,
                           @Value("${app.otp.max-attempts}") int maxAttempts,
                           @Value("${app.otp.resend-cooldown-seconds}") long resendCooldownSeconds,
                           @Value("${app.otp.verification-token-ttl-minutes}") long verificationTokenTtlMinutes) {
        this.otpRepository = otpRepository;
        this.whatsAppService = whatsAppService;
        this.accessTokenGenerator = accessTokenGenerator;
        this.mode = mode;
        this.otpLength = otpLength;
        this.expiryMinutes = expiryMinutes;
        this.maxAttempts = maxAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.verificationTokenTtlMinutes = verificationTokenTtlMinutes;
    }

    @Override
    @Transactional
    public OtpSendResponse sendOtp(SendOtpRequest request) {
        Otp otp = otpRepository.findByMobileNumberAndPurpose(request.mobileNumber(), PURPOSE)
                .orElseGet(Otp::new);
        otp.setMobileNumber(request.mobileNumber());
        otp.setCountryPhoneCode(request.countryPhoneCode());
        otp.setEmail(request.email());
        otp.setPurpose(PURPOSE);

        return issueAndDispatch(otp, request.firstName());
    }

    @Override
    @Transactional
    public OtpSendResponse resendOtp(ResendOtpRequest request) {
        Otp otp = otpRepository.findByMobileNumberAndPurpose(request.mobileNumber(), PURPOSE)
                .orElseThrow(() -> new BusinessException(
                        "No OTP request found for this number. Please start again.", "OTP_NOT_FOUND", HttpStatus.BAD_REQUEST));

        if (otp.getLastSentAt() != null
                && ChronoUnit.SECONDS.between(otp.getLastSentAt(), LocalDateTime.now()) < resendCooldownSeconds) {
            throw new BusinessException("Please wait before requesting another OTP.",
                    "OTP_RESEND_COOLDOWN", HttpStatus.TOO_MANY_REQUESTS);
        }

        otp.setCountryPhoneCode(request.countryPhoneCode());
        // First name isn't resubmitted on resend — reuse whatever the WhatsApp template
        // greeting used last time isn't critical for a one-time code, so a generic greeting is fine.
        return issueAndDispatch(otp, "there");
    }

    @Override
    // The attempts counter must survive even when this method throws (that's the whole point
    // of rate-limiting wrong guesses) — without noRollbackFor, Spring would roll back the
    // otpRepository.save(otp) below along with the BusinessException, silently resetting the
    // counter on every failed attempt and defeating the max-attempts limit entirely.
    @Transactional(noRollbackFor = BusinessException.class)
    public OtpVerifyResponse verifyOtp(VerifyOtpRequest request) {
        Otp otp = otpRepository.findByMobileNumberAndPurpose(request.mobileNumber(), PURPOSE)
                .filter(o -> o.getStatus() == OtpStatus.PENDING)
                .filter(o -> o.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new BusinessException(INVALID_MESSAGE, "OTP_INVALID", HttpStatus.BAD_REQUEST));

        if (otp.getAttempts() >= maxAttempts) {
            throw new BusinessException("Too many incorrect attempts. Please request a new OTP.",
                    "OTP_MAX_ATTEMPTS", HttpStatus.TOO_MANY_REQUESTS);
        }

        otp.setAttempts(otp.getAttempts() + 1);

        if (!otp.getOtpHash().equals(accessTokenGenerator.hash(request.code()))) {
            otpRepository.save(otp);
            throw new BusinessException(INVALID_MESSAGE, "OTP_INVALID", HttpStatus.BAD_REQUEST);
        }

        otp.setStatus(OtpStatus.VERIFIED);
        otp.setVerifiedAt(LocalDateTime.now());

        String rawVerificationToken = accessTokenGenerator.generate();
        otp.setVerificationTokenHash(accessTokenGenerator.hash(rawVerificationToken));
        otp.setVerificationTokenExpiresAt(LocalDateTime.now().plusMinutes(verificationTokenTtlMinutes));
        otpRepository.save(otp);

        return new OtpVerifyResponse(true, rawVerificationToken, "OTP verified.");
    }

    @Override
    @Transactional
    public void assertVerifiedAndConsume(String rawVerificationToken, String mobileNumber) {
        if (rawVerificationToken == null || rawVerificationToken.isBlank()) {
            throw new BusinessException("OTP verification required.", "OTP_VERIFICATION_REQUIRED", HttpStatus.UNAUTHORIZED);
        }

        String hash = accessTokenGenerator.hash(rawVerificationToken);
        Otp otp = otpRepository
                .findByVerificationTokenHashAndStatusAndVerificationTokenExpiresAtAfter(hash, OtpStatus.VERIFIED, LocalDateTime.now())
                // Same message whether the token never existed, expired, or was already consumed —
                // never reveal which, that would leak information to an attacker probing this endpoint.
                .filter(o -> o.getMobileNumber().equals(mobileNumber))
                .orElseThrow(() -> new BusinessException("OTP verification required.", "OTP_VERIFICATION_REQUIRED", HttpStatus.UNAUTHORIZED));

        otp.setStatus(OtpStatus.CONSUMED);
        otpRepository.save(otp);
    }

    private OtpSendResponse issueAndDispatch(Otp otp, String firstName) {
        String rawCode = accessTokenGenerator.generateNumericCode(otpLength);
        otp.setOtpHash(accessTokenGenerator.hash(rawCode));
        otp.setStatus(OtpStatus.PENDING);
        otp.setAttempts(0);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
        otp.setLastSentAt(LocalDateTime.now());
        // Clear any previously issued verification token — a fresh code invalidates any
        // outstanding proof-of-verification from an earlier OTP for this same number.
        otp.setVerificationTokenHash(null);
        otp.setVerificationTokenExpiresAt(null);
        otp.setVerifiedAt(null);
        otpRepository.save(otp);

        boolean isTestMode = "TEST".equalsIgnoreCase(mode);

        if (isTestMode) {
            log.info("[OTP TEST MODE] OTP for {}{}: {} (expires in {} min)",
                    otp.getCountryPhoneCode(), otp.getMobileNumber(), rawCode, expiryMinutes);
        } else {
            try {
                whatsAppService.sendOtpWhatsApp(otp.getCountryPhoneCode() + otp.getMobileNumber(), firstName, rawCode);
            } catch (WhatsAppDeliveryException ex) {
                log.warn("Failed to send OTP WhatsApp message to {}{}: {}",
                        otp.getCountryPhoneCode(), otp.getMobileNumber(), ex.getMessage());
                throw new BusinessException("Failed to send OTP. Please try again.", "OTP_SEND_FAILED", HttpStatus.BAD_GATEWAY);
            }
        }

        return new OtpSendResponse(
                "OTP sent to your WhatsApp number.",
                expiryMinutes * 60,
                isTestMode ? rawCode : null);
    }
}
