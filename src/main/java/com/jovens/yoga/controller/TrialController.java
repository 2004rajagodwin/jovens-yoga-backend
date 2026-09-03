package com.jovens.yoga.controller;

import com.jovens.yoga.dto.request.CreateTrialRequest;
import com.jovens.yoga.dto.request.TrialEligibilityRequest;
import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.dto.response.TrialEligibilityResponse;
import com.jovens.yoga.dto.response.TrialResponse;
import com.jovens.yoga.service.TrialService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/trials")
public class TrialController {

    private final TrialService trialService;

    public TrialController(TrialService trialService) {
        this.trialService = trialService;
    }

    @PostMapping("/eligibility")
    public ApiResponse<TrialEligibilityResponse> checkEligibility(@Valid @RequestBody TrialEligibilityRequest request) {
        return ApiResponse.success("Eligibility checked.", trialService.checkEligibility(request));
    }

    @PostMapping
    public ApiResponse<TrialResponse> createTrial(@Valid @RequestBody CreateTrialRequest request) {
        return ApiResponse.success("Trial activated.", trialService.createTrial(request));
    }

    // Deliberately no GET /{id} or POST /{id}/cancel here — a trial's sequential numeric id
    // must never itself grant access (that was a real IDOR). Unauthenticated access to a
    // trial's own details is only ever resolved through a cryptographically random,
    // short-lived access token (see /access/{token} below), issued by checkEligibility for a
    // returning visitor or embedded in the Stripe redirect URL for a fresh trial.

    @GetMapping("/access/{token}")
    public ApiResponse<TrialResponse> getTrialByAccessToken(@PathVariable String token) {
        return ApiResponse.success("Trial retrieved.", trialService.getTrialByAccessToken(token));
    }

    @PostMapping("/access/{token}/cancel")
    public ApiResponse<Void> cancelTrialByAccessToken(@PathVariable String token) {
        trialService.cancelTrialByAccessToken(token);
        return ApiResponse.success("Trial cancelled.", null);
    }
}
