package com.jovens.yoga.service.impl;

import com.jovens.yoga.entity.CustomerOrder;
import com.jovens.yoga.entity.Notification;
import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.entity.User;
import com.jovens.yoga.enums.NotificationChannel;
import com.jovens.yoga.enums.NotificationStatus;
import com.jovens.yoga.enums.NotificationType;
import com.jovens.yoga.exception.BusinessException;
import com.jovens.yoga.exception.EmailDeliveryException;
import com.jovens.yoga.exception.ResourceNotFoundException;
import com.jovens.yoga.exception.WhatsAppDeliveryException;
import com.jovens.yoga.repository.NotificationRepository;
import com.jovens.yoga.repository.OrderRepository;
import com.jovens.yoga.repository.PaymentRepository;
import com.jovens.yoga.repository.TrialRepository;
import com.jovens.yoga.service.EmailService;
import com.jovens.yoga.service.NotificationService;
import com.jovens.yoga.service.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Records the intent to notify a customer as an auditable, idempotent {@link Notification}
 * row per channel, then dispatches through the channel-specific provider (EmailService,
 * WhatsAppService — kept separate, never mixed). A failed send is caught here and recorded
 * as FAILED rather than propagated, so notification delivery can never roll back or break
 * the caller's business transaction (trial activation, order creation, etc.).
 *
 * WhatsApp dispatch goes through AskEvaWhatsAppServiceImpl; when AskEva isn't configured
 * (no API URL), delivery is a graceful no-op and the notification row stays PENDING —
 * Email dispatch is fully wired to SmtpEmailService.
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final int MAX_FAILURE_REASON_LENGTH = 1000;

    private static final Pattern REMINDER_DAY_PATTERN = Pattern.compile("TRIAL_REMINDER_DAY_(\\d+)_EMAIL");

    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final TrialRepository trialRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                    EmailService emailService,
                                    WhatsAppService whatsAppService,
                                    TrialRepository trialRepository,
                                    OrderRepository orderRepository,
                                    PaymentRepository paymentRepository) {
        this.notificationRepository = notificationRepository;
        this.emailService = emailService;
        this.whatsAppService = whatsAppService;
        this.trialRepository = trialRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional
    public void recordTrialActivation(User user, Trial trial) {
        recordAndDispatchEmail(user, NotificationType.TRIAL_ACTIVATION,
                "TRIAL_ACTIVATION_EMAIL_TRIAL_" + trial.getId(), "TRIAL", trial.getId(),
                () -> emailService.sendTrialActivationEmail(user, trial));
        recordAndDispatchWhatsApp(user, NotificationType.TRIAL_ACTIVATION,
                "TRIAL_ACTIVATION_WHATSAPP_TRIAL_" + trial.getId(), "TRIAL", trial.getId(),
                () -> whatsAppService.sendTrialActivationWhatsApp(user, trial));
    }

    @Override
    @Transactional
    public void recordTrialReminder(User user, Trial trial, int dayIndex) {
        int remainingDays = computeRemainingDays(trial, dayIndex);

        recordAndDispatchEmail(user, NotificationType.TRIAL_REMINDER,
                "TRIAL_REMINDER_DAY_" + dayIndex + "_EMAIL_TRIAL_" + trial.getId(), "TRIAL", trial.getId(),
                () -> emailService.sendTrialReminderEmail(user, trial, remainingDays));
        recordAndDispatchWhatsApp(user, NotificationType.TRIAL_REMINDER,
                "TRIAL_REMINDER_DAY_" + dayIndex + "_WHATSAPP_TRIAL_" + trial.getId(), "TRIAL", trial.getId(),
                () -> whatsAppService.sendTrialReminderWhatsApp(user, trial, remainingDays));
    }

    @Override
    @Transactional
    public void recordTrialExpiry(User user, Trial trial) {
        recordAndDispatchEmail(user, NotificationType.TRIAL_EXPIRY,
                "TRIAL_EXPIRY_EMAIL_TRIAL_" + trial.getId(), "TRIAL", trial.getId(),
                () -> emailService.sendTrialExpiryEmail(user, trial));
        recordAndDispatchWhatsApp(user, NotificationType.TRIAL_EXPIRY,
                "TRIAL_EXPIRY_WHATSAPP_TRIAL_" + trial.getId(), "TRIAL", trial.getId(),
                () -> whatsAppService.sendTrialExpiryWhatsApp(user, trial));
    }

    @Override
    @Transactional
    public void recordPaidPurchase(User user, CustomerOrder order) {
        recordAndDispatchEmail(user, NotificationType.PAID_PURCHASE,
                "PAID_PURCHASE_EMAIL_ORDER_" + order.getId(), "ORDER", order.getId(),
                () -> emailService.sendPaidPurchaseEmail(user, order));
        recordAndDispatchWhatsApp(user, NotificationType.PAID_PURCHASE,
                "PAID_PURCHASE_WHATSAPP_ORDER_" + order.getId(), "ORDER", order.getId(),
                () -> whatsAppService.sendPaidPurchaseWhatsApp(user, order));
    }

    @Override
    @Transactional
    public void recordTrialConversionPurchase(User user, Trial trial, Payment payment) {
        recordAndDispatchEmail(user, NotificationType.TRIAL_CONVERSION_PURCHASE,
                "TRIAL_CONVERSION_PURCHASE_EMAIL_PAYMENT_" + payment.getId(), "PAYMENT", payment.getId(),
                () -> emailService.sendTrialConversionPaidEmail(user, trial, payment));
        recordAndDispatchWhatsApp(user, NotificationType.TRIAL_CONVERSION_PURCHASE,
                "TRIAL_CONVERSION_PURCHASE_WHATSAPP_PAYMENT_" + payment.getId(), "PAYMENT", payment.getId(),
                () -> whatsAppService.sendTrialConversionPaidWhatsApp(user, trial, payment));
    }

    @Override
    @Transactional
    public void retryEmail(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + notificationId));

        if (notification.getChannel() != NotificationChannel.EMAIL) {
            throw new BusinessException("Only EMAIL notifications can be retried.", "RETRY_NOT_SUPPORTED", HttpStatus.BAD_REQUEST);
        }
        if (notification.getStatus() == NotificationStatus.SENT) {
            throw new BusinessException("This notification has already been sent and cannot be retried.", "ALREADY_SENT", HttpStatus.CONFLICT);
        }

        Supplier<String> sendAction = resolveRetryAction(notification);

        try {
            String providerReference = sendAction.get();
            notification.setStatus(NotificationStatus.SENT);
            notification.setProviderReference(providerReference);
            notification.setSentAt(LocalDateTime.now());
            notification.setFailureReason(null);
            log.info("Notification {} (event={}) retried successfully via EMAIL.", notification.getId(), notification.getEventKey());
        } catch (EmailDeliveryException ex) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason(truncate(ex.getMessage()));
            log.warn("Notification {} (event={}) retry failed via EMAIL: {}", notification.getId(), notification.getEventKey(), ex.getMessage());
        }
        notificationRepository.save(notification);
    }

    private Supplier<String> resolveRetryAction(Notification notification) {
        User user = notification.getUser();

        return switch (notification.getType()) {
            case TRIAL_ACTIVATION -> {
                Trial trial = loadTrial(notification.getReferenceId());
                yield () -> emailService.sendTrialActivationEmail(user, trial);
            }
            case TRIAL_REMINDER -> {
                Trial trial = loadTrial(notification.getReferenceId());
                int dayIndex = extractReminderDay(notification.getEventKey()).orElse(trial.getLastReminderDayIndex());
                int remainingDays = computeRemainingDays(trial, dayIndex);
                yield () -> emailService.sendTrialReminderEmail(user, trial, remainingDays);
            }
            case TRIAL_EXPIRY -> {
                Trial trial = loadTrial(notification.getReferenceId());
                yield () -> emailService.sendTrialExpiryEmail(user, trial);
            }
            case PAID_PURCHASE -> {
                CustomerOrder order = orderRepository.findById(notification.getReferenceId())
                        .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + notification.getReferenceId()));
                yield () -> emailService.sendPaidPurchaseEmail(user, order);
            }
            case TRIAL_CONVERSION_PURCHASE -> {
                Payment payment = paymentRepository.findById(notification.getReferenceId())
                        .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + notification.getReferenceId()));
                Trial trial = payment.getTrial();
                yield () -> emailService.sendTrialConversionPaidEmail(user, trial, payment);
            }
        };
    }

    private Trial loadTrial(Long trialId) {
        return trialRepository.findById(trialId)
                .orElseThrow(() -> new ResourceNotFoundException("Trial not found with id: " + trialId));
    }

    private Optional<Integer> extractReminderDay(String eventKey) {
        Matcher matcher = REMINDER_DAY_PATTERN.matcher(eventKey);
        return matcher.find() ? Optional.of(Integer.parseInt(matcher.group(1))) : Optional.empty();
    }

    private int computeRemainingDays(Trial trial, int dayIndex) {
        int totalDays = trial.getPlan().getTrialDurationDays() != null ? trial.getPlan().getTrialDurationDays() : dayIndex;
        return Math.max(totalDays - dayIndex, 0);
    }

    /**
     * Creates the EMAIL notification row if it doesn't already exist for this event key,
     * then immediately attempts delivery and updates the row to SENT or FAILED.
     * If the row already exists (any status), no email is sent again — this is the
     * idempotency guarantee.
     */
    private void recordAndDispatchEmail(User user, NotificationType type, String eventKey,
                                         String referenceType, Long referenceId, Supplier<String> sendAction) {
        Optional<Notification> created = createIfAbsent(user, type, NotificationChannel.EMAIL, eventKey, referenceType, referenceId);
        if (created.isEmpty()) {
            log.debug("Email notification for event {} already recorded, skipping duplicate send.", eventKey);
            return;
        }

        Notification notification = created.get();
        try {
            String providerReference = sendAction.get();
            notification.setStatus(NotificationStatus.SENT);
            notification.setProviderReference(providerReference);
            notification.setSentAt(LocalDateTime.now());
            log.info("Notification {} (event={}) delivered via EMAIL.", notification.getId(), eventKey);
        } catch (EmailDeliveryException ex) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason(truncate(ex.getMessage()));
            log.warn("Notification {} (event={}) failed via EMAIL: {}", notification.getId(), eventKey, ex.getMessage());
        }
        notificationRepository.save(notification);
    }

    /**
     * Creates the WHATSAPP notification row if it doesn't already exist for this event key,
     * then attempts delivery via AskEva. A {@code null} provider reference (AskEva not
     * configured) leaves the row PENDING — the same behavior the old stub always produced.
     * A thrown {@link WhatsAppDeliveryException} marks the row FAILED, mirroring the EMAIL path.
     */
    private void recordAndDispatchWhatsApp(User user, NotificationType type, String eventKey,
                                            String referenceType, Long referenceId, Supplier<String> sendAction) {
        Optional<Notification> created = createIfAbsent(user, type, NotificationChannel.WHATSAPP, eventKey, referenceType, referenceId);
        if (created.isEmpty()) {
            log.debug("WhatsApp notification for event {} already recorded, skipping duplicate send.", eventKey);
            return;
        }

        Notification notification = created.get();
        try {
            String providerReference = sendAction.get();
            if (providerReference == null) {
                log.debug("Notification {} (event={}) left PENDING — WhatsApp provider not configured.", notification.getId(), eventKey);
                return;
            }
            notification.setStatus(NotificationStatus.SENT);
            notification.setProviderReference(providerReference);
            notification.setSentAt(LocalDateTime.now());
            log.info("Notification {} (event={}) delivered via WHATSAPP.", notification.getId(), eventKey);
            notificationRepository.save(notification);
        } catch (WhatsAppDeliveryException ex) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason(truncate(ex.getMessage()));
            log.warn("Notification {} (event={}) failed via WHATSAPP: {}", notification.getId(), eventKey, ex.getMessage());
            notificationRepository.save(notification);
        }
    }

    private Optional<Notification> createIfAbsent(User user, NotificationType type, NotificationChannel channel,
                                                    String eventKey, String referenceType, Long referenceId) {
        if (notificationRepository.existsByEventKey(eventKey)) {
            return Optional.empty();
        }

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setChannel(channel);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        notification.setEventKey(eventKey);
        notification.setStatus(NotificationStatus.PENDING);

        try {
            return Optional.of(notificationRepository.save(notification));
        } catch (DataIntegrityViolationException ex) {
            // Another concurrent call (or scheduler restart) already recorded this event.
            log.debug("Notification event {} already recorded, skipping duplicate.", eventKey);
            return Optional.empty();
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_FAILURE_REASON_LENGTH ? message.substring(0, MAX_FAILURE_REASON_LENGTH) : message;
    }
}
