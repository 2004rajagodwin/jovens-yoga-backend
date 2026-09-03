package com.jovens.yoga.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates cryptographically random, unpredictable access tokens for unauthenticated
 * read/cancel access to a single resource (e.g. a trial, for the Stripe -&gt; Thank You
 * redirect flow). Never derived from a database id, email, phone, or timestamp — only a
 * SHA-256 hash of the raw token should ever be persisted, never the raw token itself.
 */
@Component
public class AccessTokenGenerator {

    private static final int TOKEN_BYTES = 32; // 256 bits of entropy
    private final SecureRandom secureRandom = new SecureRandom();

    /** Returns a new URL-safe random raw token. Give this to the caller once; store only its hash. */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Returns a random zero-padded numeric code of the given length (e.g. a 4-digit OTP). */
    public String generateNumericCode(int digits) {
        StringBuilder code = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            code.append(secureRandom.nextInt(10));
        }
        return code.toString();
    }

    /** SHA-256 hex digest of the given raw token, for storage/lookup — never store the raw token. */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
