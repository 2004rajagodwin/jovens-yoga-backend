package com.jovens.yoga.integration.email;

import com.jovens.yoga.config.FrontendProperties;
import com.jovens.yoga.config.MailProperties;
import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.entity.Plan;
import com.jovens.yoga.entity.PlanDuration;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.entity.User;
import com.jovens.yoga.enums.OrderStatus;
import com.jovens.yoga.enums.PlanType;
import com.jovens.yoga.enums.TrialStatus;
import com.jovens.yoga.exception.EmailDeliveryException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailService smtpEmailService;

    @BeforeEach
    void setUp() {
        MailProperties mailProperties = new MailProperties();
        mailProperties.setFromAddress("no-reply@jovensyoga.com");
        mailProperties.setFromName("Jovens Yoga");

        FrontendProperties frontendProperties = new FrontendProperties();
        frontendProperties.setBaseUrl("http://localhost:5173");

        smtpEmailService = new SmtpEmailService(mailSender, new EmailTemplateRenderer(), mailProperties, frontendProperties);
    }

    private MimeMessage realMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    private User user() {
        User user = new User();
        user.setFirstName("Alice");
        user.setLastName("Smith");
        user.setEmail("alice@example.com");
        user.setCountryPhoneCode("+1");
        user.setMobileNumber("5551230001");
        return user;
    }

    private Trial trial() {
        Plan plan = new Plan();
        plan.setName("Free Trial");
        plan.setPlanType(PlanType.FREE_TRIAL);
        plan.setTrialDurationDays(5);

        Trial trial = new Trial();
        trial.setPlan(plan);
        trial.setStatus(TrialStatus.TRIAL_ACTIVE);
        trial.setTrialStartDate(LocalDateTime.now());
        trial.setTrialExpiryDate(LocalDateTime.now().plusDays(5));
        return trial;
    }

    @Test
    void sendTrialActivationEmailReturnsProviderReferenceOnSuccess() {
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());

        String providerRef = smtpEmailService.sendTrialActivationEmail(user(), trial());

        assertThat(providerRef).isNotBlank();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendTrialActivationEmailThrowsEmailDeliveryExceptionOnSmtpFailure() {
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
        doThrow(new MailSendException("Connection refused")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> smtpEmailService.sendTrialActivationEmail(user(), trial()))
                .isInstanceOf(EmailDeliveryException.class);
    }

    @Test
    void sendTrialReminderEmailSucceeds() {
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());

        String providerRef = smtpEmailService.sendTrialReminderEmail(user(), trial(), 2);

        assertThat(providerRef).isNotBlank();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendTrialExpiryEmailSucceeds() {
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());

        String providerRef = smtpEmailService.sendTrialExpiryEmail(user(), trial());

        assertThat(providerRef).isNotBlank();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPaidPurchaseEmailSucceeds() {
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());

        Plan plan = new Plan();
        plan.setName("Premium");

        PlanDuration duration = new PlanDuration();
        duration.setDurationLabel("Per Month");

        CustomerOrder order = new CustomerOrder();
        order.setId(1L);
        order.setOrderNumber("ORD-1001");
        order.setPlan(plan);
        order.setPlanDuration(duration);
        order.setPlanNameSnapshot("Premium");
        order.setDurationLabelSnapshot("Per Month");
        order.setAmount(new BigDecimal("59.00"));
        order.setCurrency("USD");
        order.setStatus(OrderStatus.PAID);

        String providerRef = smtpEmailService.sendPaidPurchaseEmail(user(), order);

        assertThat(providerRef).isNotBlank();
        verify(mailSender).send(any(MimeMessage.class));
    }
}
