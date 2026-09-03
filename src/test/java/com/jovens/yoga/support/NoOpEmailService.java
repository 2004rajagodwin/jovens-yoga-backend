package com.jovens.yoga.support;

import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.entity.User;
import com.jovens.yoga.service.EmailService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Test-profile replacement for SmtpEmailService so @SpringBootTest-based tests never
 * attempt a real network call to Gmail SMTP. Dedicated EmailService/SmtpEmailService
 * behavior (success/failure, template rendering) is covered separately with a mocked
 * JavaMailSender.
 */
@Service
@Profile("test")
public class NoOpEmailService implements EmailService {

    @Override
    public String sendTrialActivationEmail(User user, Trial trial) {
        return UUID.randomUUID().toString();
    }

    @Override
    public String sendTrialReminderEmail(User user, Trial trial, int remainingDays) {
        return UUID.randomUUID().toString();
    }

    @Override
    public String sendTrialExpiryEmail(User user, Trial trial) {
        return UUID.randomUUID().toString();
    }

    @Override
    public String sendPaidPurchaseEmail(User user, CustomerOrder order) {
        return UUID.randomUUID().toString();
    }

    @Override
    public String sendTrialConversionPaidEmail(User user, Trial trial, Payment payment) {
        return UUID.randomUUID().toString();
    }
}
