package com.jovens.yoga.service;

import com.jovens.yoga.entity.Notification;
import com.jovens.yoga.entity.Plan;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.entity.User;
import com.jovens.yoga.enums.NotificationChannel;
import com.jovens.yoga.enums.NotificationStatus;
import com.jovens.yoga.enums.NotificationType;
import com.jovens.yoga.enums.PlanType;
import com.jovens.yoga.enums.TrialStatus;
import com.jovens.yoga.exception.BusinessException;
import com.jovens.yoga.exception.EmailDeliveryException;
import com.jovens.yoga.repository.NotificationRepository;
import com.jovens.yoga.repository.OrderRepository;
import com.jovens.yoga.repository.PaymentRepository;
import com.jovens.yoga.repository.TrialRepository;
import com.jovens.yoga.service.impl.NotificationServiceImpl;
import com.jovens.yoga.service.WhatsAppService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private TrialRepository trialRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WhatsAppService whatsAppService;

    @Mock
    private PaymentRepository paymentRepository;

    private NotificationServiceImpl notificationService;

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setFirstName("Alice");
        user.setLastName("Smith");
        user.setEmail("alice@example.com");
        return user;
    }

    private Trial trial() {
        Plan plan = new Plan();
        plan.setId(10L);
        plan.setName("Free Trial");
        plan.setPlanType(PlanType.FREE_TRIAL);
        plan.setTrialDurationDays(5);

        Trial trial = new Trial();
        trial.setId(100L);
        trial.setPlan(plan);
        trial.setStatus(TrialStatus.TRIAL_ACTIVE);
        trial.setTrialStartDate(LocalDateTime.now());
        trial.setTrialExpiryDate(LocalDateTime.now().plusDays(5));
        return trial;
    }

    private NotificationServiceImpl service() {
        return new NotificationServiceImpl(notificationRepository, emailService, whatsAppService, trialRepository, orderRepository, paymentRepository);
    }

    @Test
    void successfulEmailMarksNotificationSent() {
        notificationService = service();
        when(notificationRepository.existsByEventKey(anyString())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(emailService.sendTrialActivationEmail(any(), any())).thenReturn("msg-123");

        notificationService.recordTrialActivation(user(), trial());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());

        Notification emailNotification = captor.getAllValues().stream()
                .filter(n -> n.getChannel() == NotificationChannel.EMAIL)
                .reduce((first, second) -> second) // last save call for the EMAIL row = final state
                .orElseThrow();

        assertThat(emailNotification.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(emailNotification.getProviderReference()).isEqualTo("msg-123");
    }

    @Test
    void failedEmailMarksNotificationFailedWithoutThrowing() {
        notificationService = service();
        when(notificationRepository.existsByEventKey(anyString())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(emailService.sendTrialActivationEmail(any(), any()))
                .thenThrow(new EmailDeliveryException("SMTP connection refused", new RuntimeException()));

        // Must not throw — a notification failure must never break the caller's business flow.
        notificationService.recordTrialActivation(user(), trial());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, atLeastOnce()).save(captor.capture());

        Notification emailNotification = captor.getAllValues().stream()
                .filter(n -> n.getChannel() == NotificationChannel.EMAIL)
                .reduce((first, second) -> second)
                .orElseThrow();

        assertThat(emailNotification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(emailNotification.getFailureReason()).contains("SMTP connection refused");
    }

    @Test
    void duplicateEventKeyDoesNotSendEmailAgain() {
        notificationService = service();
        when(notificationRepository.existsByEventKey(anyString())).thenReturn(true);

        notificationService.recordTrialActivation(user(), trial());

        verifyNoInteractions(emailService);
        verify(notificationRepository, never()).save(any());
    }

    private Notification failedTrialActivationNotification() {
        Notification notification = new Notification();
        notification.setId(500L);
        notification.setUser(user());
        notification.setType(NotificationType.TRIAL_ACTIVATION);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setEventKey("TRIAL_ACTIVATION_EMAIL_TRIAL_100");
        notification.setReferenceType("TRIAL");
        notification.setReferenceId(100L);
        notification.setStatus(NotificationStatus.FAILED);
        return notification;
    }

    @Test
    void retryingFailedNotificationResendsAndMarksSent() {
        notificationService = service();
        Notification failed = failedTrialActivationNotification();

        when(notificationRepository.findById(500L)).thenReturn(Optional.of(failed));
        when(trialRepository.findById(100L)).thenReturn(Optional.of(trial()));
        when(emailService.sendTrialActivationEmail(any(), any())).thenReturn("msg-retry-1");
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.retryEmail(500L);

        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(failed.getProviderReference()).isEqualTo("msg-retry-1");
        verify(emailService, times(1)).sendTrialActivationEmail(any(), any());
    }

    @Test
    void retryingAlreadySentNotificationIsRejected() {
        notificationService = service();
        Notification sent = failedTrialActivationNotification();
        sent.setStatus(NotificationStatus.SENT);

        when(notificationRepository.findById(500L)).thenReturn(Optional.of(sent));

        assertThatThrownBy(() -> notificationService.retryEmail(500L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been sent");

        verifyNoInteractions(emailService);
    }
}
