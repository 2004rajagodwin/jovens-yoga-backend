package com.jovens.yoga.dto.response;

/**
 * @param status "ELIGIBLE" for a new/never-used visitor, "TRIAL_ACTIVE" when this email/mobile
 *               already has a running trial, "TRIAL_EXPIRED" when their trial ended without ever
 *               successfully converting (they still need to pay), "ACTIVE" when their trial
 *               already converted to a real paid subscription (they are a paying member — never
 *               offer them checkout again, that risks a duplicate charge). The frontend must
 *               render a distinct UI for each.
 * @param accessToken a freshly issued, short-lived, cryptographically random token scoped to the
 *                     existing blocking trial, so the frontend can fetch its details via
 *                     {@code GET /api/trials/access/{token}} — never the trial's raw numeric id
 *                     (that endpoint no longer exists publicly; see the IDOR fix). Null when eligible.
 */
public record TrialEligibilityResponse(boolean eligible, String message, String status, String accessToken) {
}
