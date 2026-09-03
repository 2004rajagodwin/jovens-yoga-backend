package com.jovens.yoga.repository;

import com.jovens.yoga.entity.Otp;
import com.jovens.yoga.enums.OtpPurpose;
import com.jovens.yoga.enums.OtpStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OtpRepository extends JpaRepository<Otp, Long> {
    Optional<Otp> findByMobileNumberAndPurpose(String mobileNumber, OtpPurpose purpose);

    Optional<Otp> findByVerificationTokenHashAndStatusAndVerificationTokenExpiresAtAfter(
            String verificationTokenHash, OtpStatus status, LocalDateTime now);
}
