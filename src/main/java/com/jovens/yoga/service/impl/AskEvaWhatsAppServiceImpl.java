package com.jovens.yoga.service.impl;

import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.entity.User;
import com.jovens.yoga.integration.whatsapp.AskEvaClient;
import com.jovens.yoga.service.WhatsAppService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * AskEva-backed {@link WhatsAppService}. This is the only class in the codebase that talks
 * to {@link AskEvaClient} directly, mirroring how {@code SmtpEmailService} is the sole
 * EmailService implementation.
 */
@Service
public class AskEvaWhatsAppServiceImpl implements WhatsAppService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final AskEvaClient askEvaClient;

    public AskEvaWhatsAppServiceImpl(AskEvaClient askEvaClient) {
        this.askEvaClient = askEvaClient;
    }

    @Override
    public String sendTrialActivationWhatsApp(User user, Trial trial) {
        Map<String, String> params = baseParams(user);
        params.put("planName", trial.getPlan().getName());
        params.put("durationDays", String.valueOf(durationDays(trial)));
        params.put("trialStartDate", trial.getTrialStartDate().format(DATE_FORMAT));
        params.put("trialExpiryDate", trial.getTrialExpiryDate().format(DATE_FORMAT));
        return askEvaClient.sendTemplateMessage(toPhoneE164(user), params);
    }

    @Override
    public String sendTrialReminderWhatsApp(User user, Trial trial, int remainingDays) {
        Map<String, String> params = baseParams(user);
        params.put("planName", trial.getPlan().getName());
        params.put("remainingDays", String.valueOf(remainingDays));
        params.put("trialExpiryDate", trial.getTrialExpiryDate().format(DATE_FORMAT));
        return askEvaClient.sendTemplateMessage(toPhoneE164(user), params);
    }

    @Override
    public String sendTrialExpiryWhatsApp(User user, Trial trial) {
        Map<String, String> params = baseParams(user);
        params.put("trialEndDate", trial.getTrialExpiryDate().format(DATE_FORMAT));
        return askEvaClient.sendTemplateMessage(toPhoneE164(user), params);
    }

    @Override
    public String sendPaidPurchaseWhatsApp(User user, CustomerOrder order) {
        Map<String, String> params = baseParams(user);
        params.put("planName", order.getPlanNameSnapshot());
        params.put("duration", order.getDurationLabelSnapshot());
        params.put("amount", formatAmount(order.getAmount()));
        params.put("currency", order.getCurrency());
        params.put("orderId", order.getOrderNumber());
        return askEvaClient.sendTemplateMessage(toPhoneE164(user), params);
    }

    @Override
    public String sendTrialConversionPaidWhatsApp(User user, Trial trial, Payment payment) {
        Map<String, String> params = baseParams(user);
        params.put("planName", trial.getPlan().getName());
        params.put("trialExpiryDate", trial.getTrialExpiryDate() != null ? trial.getTrialExpiryDate().format(DATE_FORMAT) : "");
        params.put("paymentDate", (payment.getPaidAt() != null ? payment.getPaidAt() : java.time.LocalDateTime.now()).format(DATE_FORMAT));
        params.put("amount", formatAmount(payment.getAmount()));
        params.put("currency", payment.getCurrency());
        return askEvaClient.sendTemplateMessage(toPhoneE164(user), params);
    }

    @Override
    public String sendOtpWhatsApp(String toPhoneE164, String firstName, String otpCode) {
        Map<String, String> params = new HashMap<>();
        params.put("customerName", firstName);
        params.put("otpCode", otpCode);
        return askEvaClient.sendOtpTemplateMessage(toPhoneE164, params);
    }

    private Map<String, String> baseParams(User user) {
        Map<String, String> params = new HashMap<>();
        params.put("customerName", user.getFirstName() + " " + user.getLastName());
        return params;
    }

    private int durationDays(Trial trial) {
        return trial.getPlan().getTrialDurationDays() != null
                ? trial.getPlan().getTrialDurationDays()
                : (int) java.time.temporal.ChronoUnit.DAYS.between(trial.getTrialStartDate(), trial.getTrialExpiryDate());
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String toPhoneE164(User user) {
        return user.getCountryPhoneCode() + user.getMobileNumber();
    }
}
