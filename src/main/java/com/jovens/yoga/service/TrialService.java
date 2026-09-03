package com.jovens.yoga.service;

import com.jovens.yoga.dto.request.CreateTrialRequest;
import com.jovens.yoga.dto.request.TrialEligibilityRequest;
import com.jovens.yoga.dto.response.TrialEligibilityResponse;
import com.jovens.yoga.dto.response.TrialResponse;

public interface TrialService {

    TrialEligibilityResponse checkEligibility(TrialEligibilityRequest request);

    TrialResponse createTrial(CreateTrialRequest request);

    /** Internal/admin use only — never expose this by raw id over a public HTTP route (IDOR). */
    TrialResponse getTrial(Long id);

    /** Marks a still-TRIAL_PENDING_PAYMENT trial CANCELLED. No-op if already ACTIVE/EXPIRED. */
    void cancelTrial(Long id);

    /**
     * Public, unauthenticated lookup for the Stripe -> Thank You redirect flow and for a
     * returning visitor's "trial already active/expired" detail view — resolved by a
     * cryptographically random, short-lived access token, never by the trial's raw id.
     */
    TrialResponse getTrialByAccessToken(String rawToken);

    /** Same safety semantics as {@link #cancelTrial(Long)}, resolved by access token instead of id. */
    void cancelTrialByAccessToken(String rawToken);
}
