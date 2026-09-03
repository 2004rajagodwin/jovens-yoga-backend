package com.jovens.yoga.service.impl;

import com.jovens.yoga.dto.request.CreateTrialRequest;
import com.jovens.yoga.dto.request.TrialEligibilityRequest;
import com.jovens.yoga.dto.response.TrialEligibilityResponse;
import com.jovens.yoga.dto.response.TrialResponse;
import com.jovens.yoga.entity.ClassSlot;
import com.jovens.yoga.entity.Plan;
import com.jovens.yoga.entity.PlanDuration;
import com.jovens.yoga.entity.Trial;
import com.jovens.yoga.entity.Payment;
import com.jovens.yoga.entity.User;
import com.jovens.yoga.enums.PaymentStatus;
import com.jovens.yoga.enums.PlanType;
import com.jovens.yoga.enums.TrialStatus;
import com.jovens.yoga.exception.BusinessException;
import com.jovens.yoga.exception.ResourceNotFoundException;
import com.jovens.yoga.exception.TrialAlreadyUsedException;
import com.jovens.yoga.mapper.TrialMapper;
import com.jovens.yoga.repository.PaymentRepository;
import com.jovens.yoga.repository.PlanDurationRepository;
import com.jovens.yoga.repository.TrialRepository;
import com.jovens.yoga.repository.UserRepository;
import com.jovens.yoga.security.AccessTokenGenerator;
import com.jovens.yoga.service.OtpService;
import com.jovens.yoga.service.PlanService;
import com.jovens.yoga.service.SlotService;
import com.jovens.yoga.service.TrialService;
import com.jovens.yoga.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TrialServiceImpl implements TrialService {

    private static final String ALREADY_USED_MESSAGE =
            "Your free trial has already been used. Please choose a Standard or Premium plan.";

    private final TrialRepository trialRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final PlanService planService;
    private final PlanDurationRepository planDurationRepository;
    private final SlotService slotService;
    private final TrialMapper trialMapper;
    private final PaymentRepository paymentRepository;
    private final AccessTokenGenerator accessTokenGenerator;
    private final OtpService otpService;

    /** How long an unauthenticated trial access token stays valid — long enough for a Stripe
     *  redirect/page load or a genuine return visit, short enough not to be a standing credential. */
    private static final long ACCESS_TOKEN_TTL_MINUTES = 30;

    public TrialServiceImpl(TrialRepository trialRepository,
                             UserRepository userRepository,
                             UserService userService,
                             PlanService planService,
                             PlanDurationRepository planDurationRepository,
                             SlotService slotService,
                             TrialMapper trialMapper,
                             PaymentRepository paymentRepository,
                             AccessTokenGenerator accessTokenGenerator,
                             OtpService otpService) {
        this.trialRepository = trialRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.planService = planService;
        this.planDurationRepository = planDurationRepository;
        this.slotService = slotService;
        this.trialMapper = trialMapper;
        this.paymentRepository = paymentRepository;
        this.accessTokenGenerator = accessTokenGenerator;
        this.otpService = otpService;
    }

    private static final String ALREADY_ACTIVE_MESSAGE = "Your free trial is already active.";
    private static final String ALREADY_PAID_MESSAGE = "You already have an active paid membership.";

    @Override
    @Transactional
    public TrialEligibilityResponse checkEligibility(TrialEligibilityRequest request) {
        Optional<Trial> blocking = findBlockingTrial(request.email(), request.mobileNumber());

        if (blocking.isPresent()) {
            Trial trial = blocking.get();
            String status;
            String message;
            if (trial.getStatus() == TrialStatus.TRIAL_ACTIVE) {
                status = "TRIAL_ACTIVE";
                message = ALREADY_ACTIVE_MESSAGE;
            } else if (paymentRepository.findFirstByTrialIdAndStatusOrderByPaidAtDesc(trial.getId(), PaymentStatus.PAID).isPresent()) {
                // The trial already converted to a real paid subscription — this is an active,
                // paying member, NOT someone who still needs to pay. Never offer them a "Pay Now"
                // checkout here, that risks a duplicate charge on top of their live subscription.
                status = "ACTIVE";
                message = ALREADY_PAID_MESSAGE;
            } else {
                status = "TRIAL_EXPIRED";
                message = ALREADY_USED_MESSAGE;
            }
            // The caller proved they know this trial's email+mobile pair, so it's safe to hand
            // them a fresh, short-lived access token here — never the raw database id.
            String token = issueAccessToken(trial);
            return new TrialEligibilityResponse(false, message, status, token);
        }
        return new TrialEligibilityResponse(true, "You're eligible for a free trial.", "ELIGIBLE", null);
    }

    @Override
    @Transactional
    public TrialResponse createTrial(CreateTrialRequest request) {
        otpService.assertVerifiedAndConsume(request.otpToken(), request.customer().mobileNumber());

        Plan plan = planService.getActivePlanEntity(request.planId());
        if (plan.getPlanType() != PlanType.STANDARD && plan.getPlanType() != PlanType.PREMIUM) {
            throw new BusinessException("The selected plan is not eligible for a free trial.", "INVALID_PLAN_TYPE", HttpStatus.BAD_REQUEST);
        }

        PlanDuration duration = planDurationRepository.findById(request.planDurationId())
                .orElseThrow(() -> new ResourceNotFoundException("Duration not found with id: " + request.planDurationId()));

        if (duration.getPlan() == null || !duration.getPlan().getId().equals(plan.getId())) {
            throw new BusinessException("The selected duration does not belong to the selected plan.",
                    "DURATION_PLAN_MISMATCH", HttpStatus.BAD_REQUEST);
        }
        if (!duration.isActive()) {
            throw new BusinessException("The selected duration is not currently available.",
                    "DURATION_INACTIVE", HttpStatus.BAD_REQUEST);
        }

        ClassSlot slot = slotService.getActiveSlotEntity(request.slotId());

        User user = userService.findOrCreateCustomer(request.customer());

        Optional<Trial> existing = trialRepository.findByUserId(user.getId());

        Trial trial;
        if (existing.isPresent()) {
            Trial existingTrial = existing.get();
            if (existingTrial.getStatus() == TrialStatus.TRIAL_ACTIVE || existingTrial.getStatus() == TrialStatus.TRIAL_EXPIRED) {
                throw new TrialAlreadyUsedException();
            }
            // TRIAL_PENDING_PAYMENT or CANCELLED: reuse the same row (user_id is unique).
            trial = existingTrial;
            trial.setTrialStartDate(null);
            trial.setTrialExpiryDate(null);
        } else {
            trial = new Trial();
            trial.setUser(user);
        }

        trial.setPlan(plan);
        trial.setPlanDuration(duration);
        trial.setSelectedSlot(slot);
        trial.setStatus(TrialStatus.TRIAL_PENDING_PAYMENT);

        Trial saved;
        try {
            saved = trialRepository.saveAndFlush(trial);
        } catch (DataIntegrityViolationException ex) {
            // Unique constraint on trials.user_id caught a concurrent duplicate request.
            throw new TrialAlreadyUsedException();
        }

        // Issued once, here, and handed straight back to the caller who just legitimately
        // created this pending trial — the browser's only credential for every subsequent
        // step (Stripe checkout-session creation, Thank You page). The trial's raw id is
        // never used as an authorization credential anywhere in the customer-facing API.
        String accessToken = issueAccessToken(saved);
        return trialMapper.toResponse(saved, null, accessToken);
    }

    @Override
    @Transactional(readOnly = true)
    public TrialResponse getTrial(Long id) {
        Trial trial = trialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trial not found with id: " + id));
        Payment latestPayment = paymentRepository
                .findFirstByTrialIdAndStatusOrderByPaidAtDesc(id, PaymentStatus.PAID)
                .orElse(null);
        return trialMapper.toResponse(trial, latestPayment);
    }

    @Override
    @Transactional
    public void cancelTrial(Long id) {
        Trial trial = trialRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trial not found with id: " + id));
        cancelIfPending(trial);
    }

    @Override
    @Transactional(readOnly = true)
    public TrialResponse getTrialByAccessToken(String rawToken) {
        Trial trial = resolveByAccessToken(rawToken);
        Payment latestPayment = paymentRepository
                .findFirstByTrialIdAndStatusOrderByPaidAtDesc(trial.getId(), PaymentStatus.PAID)
                .orElse(null);
        return trialMapper.toResponse(trial, latestPayment);
    }

    @Override
    @Transactional
    public void cancelTrialByAccessToken(String rawToken) {
        cancelIfPending(resolveByAccessToken(rawToken));
    }

    private void cancelIfPending(Trial trial) {
        if (trial.getStatus() == TrialStatus.TRIAL_PENDING_PAYMENT) {
            trial.setStatus(TrialStatus.CANCELLED);
        }
    }

    /**
     * Generates a new random access token for this trial, stores only its SHA-256 hash plus a
     * short expiry, and returns the raw token — the one and only time it exists outside this
     * method. Callers must hand it straight to the client (query param, Stripe redirect URL);
     * it is never persisted or logged in raw form.
     */
    private String issueAccessToken(Trial trial) {
        String rawToken = accessTokenGenerator.generate();
        trial.setAccessTokenHash(accessTokenGenerator.hash(rawToken));
        trial.setAccessTokenExpiresAt(LocalDateTime.now().plusMinutes(ACCESS_TOKEN_TTL_MINUTES));
        trialRepository.save(trial);
        return rawToken;
    }

    private Trial resolveByAccessToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResourceNotFoundException("Trial access token is missing or invalid.");
        }
        String hash = accessTokenGenerator.hash(rawToken);
        return trialRepository.findByAccessTokenHashAndAccessTokenExpiresAtAfter(hash, LocalDateTime.now())
                // Deliberately the same message/exception whether the token never existed or has
                // expired — never reveal which, that would itself leak information to an attacker.
                .orElseThrow(() -> new ResourceNotFoundException("Trial access token is missing or invalid."));
    }

    /**
     * Finds this email/mobile's existing TRIAL_ACTIVE or TRIAL_EXPIRED trial, if any — the two
     * statuses that mean "this person already claimed a trial." A TRIAL_PENDING_PAYMENT or
     * CANCELLED trial does NOT block a retry (see {@link #createTrial}).
     */
    private Optional<Trial> findBlockingTrial(String email, String mobileNumber) {
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(email);
        Optional<Trial> viaEmail = byEmail.flatMap(user -> trialRepository.findByUserId(user.getId()))
                .filter(this::isBlockingStatus);
        if (viaEmail.isPresent()) {
            return viaEmail;
        }

        Optional<User> byMobile = userRepository.findByMobileNumber(mobileNumber);
        return byMobile.flatMap(user -> trialRepository.findByUserId(user.getId()))
                .filter(this::isBlockingStatus);
    }

    private boolean isBlockingStatus(Trial trial) {
        return trial.getStatus() == TrialStatus.TRIAL_ACTIVE || trial.getStatus() == TrialStatus.TRIAL_EXPIRED;
    }
}
