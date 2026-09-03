-- WhatsApp OTP verification gate, inserted after "User Details" in both the free-trial
-- and paid checkout flows. Only a SHA-256 hash of the OTP code (and of the short-lived
-- verification token issued after a correct OTP) is ever stored — never the raw values,
-- mirroring the trial access-token pattern from V8.
CREATE TABLE otps (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    mobile_number VARCHAR(20) NOT NULL,
    country_phone_code VARCHAR(10) NOT NULL,
    email VARCHAR(255) NULL,
    purpose VARCHAR(40) NOT NULL,
    otp_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    expires_at DATETIME NOT NULL,
    last_sent_at DATETIME NOT NULL,
    verified_at DATETIME NULL,
    verification_token_hash VARCHAR(64) NULL,
    verification_token_expires_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uq_otps_mobile_purpose UNIQUE (mobile_number, purpose)
);

CREATE INDEX idx_otps_verification_token_hash ON otps (verification_token_hash);
