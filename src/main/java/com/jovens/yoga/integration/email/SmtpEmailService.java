package com.jovens.yoga.integration.email;

import com.jovens.yoga.config.FrontendProperties;
import com.jovens.yoga.config.MailProperties;
import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.entity.User;
import com.jovens.yoga.exception.EmailDeliveryException;
import com.jovens.yoga.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.jovens.yoga.integration.email.EmailTemplateRenderer.escapeHtml;

/**
 * SMTP-backed {@link EmailService}. This is the only class in the codebase that talks
 * to JavaMailSender directly — provider-specific concerns (Gmail SMTP, MIME building)
 * stay isolated here so the rest of the app depends only on the EmailService contract.
 */
@Service
@Profile("!test")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final JavaMailSender mailSender;
    private final EmailTemplateRenderer templateRenderer;
    private final MailProperties mailProperties;
    private final FrontendProperties frontendProperties;

    public SmtpEmailService(JavaMailSender mailSender,
                             EmailTemplateRenderer templateRenderer,
                             MailProperties mailProperties,
                             FrontendProperties frontendProperties) {
        this.mailSender = mailSender;
        this.templateRenderer = templateRenderer;
        this.mailProperties = mailProperties;
        this.frontendProperties = frontendProperties;
    }

    @Override
    public String sendTrialActivationEmail(User user, Trial trial) {
        Map<String, String> vars = baseVariables(user);
        vars.put("planName", escapeHtml(trial.getPlan().getName()));
        vars.put("durationDays", String.valueOf(durationDays(trial)));
        vars.put("trialStartDate", trial.getTrialStartDate().format(DATE_FORMAT));
        vars.put("trialExpiryDate", trial.getTrialExpiryDate().format(DATE_FORMAT));
        vars.put("buyPlanUrl", frontendProperties.getBaseUrl());

        String subject = "Welcome to Jovens Yoga – Your " + durationDays(trial) + "-Day Trial Is Active";
        String html = templateRenderer.render("trial-activation.html", vars);
        return send(user.getEmail(), subject, html);
    }

    @Override
    public String sendTrialReminderEmail(User user, Trial trial, int remainingDays) {
        Map<String, String> vars = baseVariables(user);
        vars.put("planName", escapeHtml(trial.getPlan().getName()));
        vars.put("remainingDays", String.valueOf(remainingDays));
        vars.put("trialExpiryDate", trial.getTrialExpiryDate().format(DATE_FORMAT));
        vars.put("buyPlanUrl", frontendProperties.getBaseUrl());

        String subject = "Your Jovens Yoga Trial – " + remainingDays + " Day" + (remainingDays == 1 ? "" : "s") + " Remaining";
        String html = templateRenderer.render("trial-reminder.html", vars);
        return send(user.getEmail(), subject, html);
    }

    @Override
    public String sendTrialExpiryEmail(User user, Trial trial) {
        Map<String, String> vars = baseVariables(user);
        vars.put("trialEndDate", trial.getTrialExpiryDate().format(DATE_FORMAT));
        vars.put("buyPlanUrl", frontendProperties.getBaseUrl());

        String subject = "Your Jovens Yoga Trial Has Ended";
        String html = templateRenderer.render("trial-expiry.html", vars);
        return send(user.getEmail(), subject, html);
    }

    @Override
    public String sendPaidPurchaseEmail(User user, CustomerOrder order) {
        Map<String, String> vars = baseVariables(user);
        vars.put("planName", escapeHtml(order.getPlanNameSnapshot()));
        vars.put("duration", escapeHtml(order.getDurationLabelSnapshot()));
        vars.put("amount", formatAmount(order.getAmount()));
        vars.put("currency", order.getCurrency());
        vars.put("orderId", order.getOrderNumber());
        vars.put("status", order.getStatus().name());

        String subject = "Jovens Yoga – Payment Confirmed";
        String html = templateRenderer.render("paid-purchase.html", vars);
        return send(user.getEmail(), subject, html);
    }

    @Override
    public String sendTrialConversionPaidEmail(User user, Trial trial, Payment payment) {
        Map<String, String> vars = baseVariables(user);
        vars.put("planName", escapeHtml(trial.getPlan().getName()));
        vars.put("trialExpiryDate", trial.getTrialExpiryDate() != null ? trial.getTrialExpiryDate().format(DATE_FORMAT) : "");
        vars.put("paymentDate", (payment.getPaidAt() != null ? payment.getPaidAt() : java.time.LocalDateTime.now()).format(DATE_FORMAT));
        vars.put("amount", formatAmount(payment.getAmount()));
        vars.put("currency", payment.getCurrency());

        String subject = "Jovens Yoga – Your " + trial.getPlan().getName() + " Plan Is Now Active";
        String html = templateRenderer.render("trial-conversion-paid.html", vars);
        return send(user.getEmail(), subject, html);
    }

    private Map<String, String> baseVariables(User user) {
        Map<String, String> vars = new HashMap<>();
        vars.put("customerName", escapeHtml(user.getFirstName() + " " + user.getLastName()));
        return vars;
    }

    private int durationDays(Trial trial) {
        return trial.getPlan().getTrialDurationDays() != null
                ? trial.getPlan().getTrialDurationDays()
                : (int) java.time.temporal.ChronoUnit.DAYS.between(trial.getTrialStartDate(), trial.getTrialExpiryDate());
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String send(String toAddress, String subject, String html) {
        String messageId = UUID.randomUUID().toString();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(mailProperties.getFromAddress(), mailProperties.getFromName());
            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(html, true);
            message.setHeader("X-Jovens-Correlation-Id", messageId);

            mailSender.send(message);
            log.info("Email sent (correlationId={}, subject='{}')", messageId, subject);
            return messageId;
        } catch (MailException | java.io.UnsupportedEncodingException | jakarta.mail.MessagingException ex) {
            log.warn("Email delivery failed (correlationId={}, subject='{}'): {}", messageId, subject, ex.getMessage());
            throw new EmailDeliveryException("Failed to send email: " + ex.getMessage(), ex);
        }
    }
}
