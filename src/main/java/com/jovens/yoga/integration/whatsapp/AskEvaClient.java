package com.jovens.yoga.integration.whatsapp;

import com.jovens.yoga.exception.WhatsAppDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * The only class that talks to the AskEva WhatsApp API directly. Keeps the provider
 * integration isolated from business logic, mirroring how {@code StripeCheckoutClient}
 * is the sole entry point for Stripe.
 *
 * When {@code app.askeva.api-url} is unset (no real credentials configured, e.g. local
 * dev), this is a graceful no-op: no HTTP call is made and {@code null} is returned,
 * matching the previous PENDING-stub behavior.
 */
@Component
public class AskEvaClient {

    private static final Logger log = LoggerFactory.getLogger(AskEvaClient.class);

    private final String apiUrl;
    private final String apiKey;
    private final String templateId;
    private final String otpTemplateId;
    private final RestTemplate restTemplate;

    public AskEvaClient(@Value("${app.askeva.api-url}") String apiUrl,
                         @Value("${app.askeva.api-key}") String apiKey,
                         @Value("${app.askeva.template-id}") String templateId,
                         @Value("${app.askeva.otp-template-id}") String otpTemplateId) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.templateId = templateId;
        this.otpTemplateId = otpTemplateId;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Sends a templated WhatsApp message via AskEva. Returns a provider reference id on
     * success, {@code null} when AskEva isn't configured (no-op), and throws
     * {@link WhatsAppDeliveryException} on any other failure.
     */
    public String sendTemplateMessage(String toPhoneE164, Map<String, String> params) {
        return send(toPhoneE164, templateId, params);
    }

    /** Same as {@link #sendTemplateMessage} but uses the dedicated OTP template id. */
    public String sendOtpTemplateMessage(String toPhoneE164, Map<String, String> params) {
        return send(toPhoneE164, otpTemplateId, params);
    }

    private String send(String toPhoneE164, String templateIdToUse, Map<String, String> params) {
        if (apiUrl == null || apiUrl.isBlank()) {
            log.debug("AskEva not configured, skipping WhatsApp dispatch");
            return null;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("to", toPhoneE164);
        body.put("templateId", templateIdToUse);
        body.put("params", params);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            var response = restTemplate.exchange(apiUrl, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            HttpStatusCode status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                throw new WhatsAppDeliveryException("AskEva responded with status " + status.value() + ": " + response.getBody());
            }

            Map<?, ?> responseBody = response.getBody();
            if (responseBody == null) {
                return null;
            }
            Object reference = responseBody.containsKey("id") ? responseBody.get("id") : responseBody.get("messageId");
            return reference != null ? reference.toString() : null;
        } catch (RestClientException ex) {
            log.warn("AskEva WhatsApp delivery failed: {}", ex.getMessage());
            throw new WhatsAppDeliveryException("Failed to send WhatsApp message: " + ex.getMessage(), ex);
        }
    }
}
