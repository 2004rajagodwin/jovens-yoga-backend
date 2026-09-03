package com.jovens.yoga.controller;

import com.jovens.yoga.dto.request.CreateCheckoutRequest;
import com.jovens.yoga.dto.response.ApiResponse;
import com.jovens.yoga.dto.response.CheckoutResponse;
import com.jovens.yoga.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/checkout")
    public ApiResponse<CheckoutResponse> createCheckout(@Valid @RequestBody CreateCheckoutRequest request) {
        return ApiResponse.success("Checkout session created.", paymentService.createCheckoutSession(request.orderNumber()));
    }

    // Deliberately no {trialId} path variable — a trial's sequential id must never be an
    // authorization credential in this API. Resolved by the same short-lived access token
    // used everywhere else in the trial flow (see the trial-detail IDOR fix).
    @PostMapping("/trials/access/{token}/checkout")
    public ApiResponse<CheckoutResponse> createTrialCheckout(@PathVariable String token) {
        return ApiResponse.success("Trial checkout session created.", paymentService.createTrialCheckoutSession(token));
    }

    /**
     * Stripe webhook endpoint. Must receive the raw request body (required for signature
     * verification) — do not let Spring deserialize this into a typed object.
     */
    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> handleStripeWebhook(@RequestBody String payload,
                                                        @RequestHeader("Stripe-Signature") String signatureHeader) {
        paymentService.handleStripeWebhook(payload, signatureHeader);
        return ResponseEntity.ok("");
    }
}
