package com.jovens.yoga.entity;

import com.jovens.yoga.enums.TrialStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "trials")
public class Trial extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_duration_id")
    private PlanDuration planDuration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_slot_id")
    private ClassSlot selectedSlot;

    @Column(name = "trial_start_date")
    private LocalDateTime trialStartDate;

    @Column(name = "trial_expiry_date")
    private LocalDateTime trialExpiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrialStatus status;

    @Column(name = "last_reminder_day_index", nullable = false)
    private int lastReminderDayIndex = 0;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Column(name = "stripe_checkout_session_id")
    private String stripeCheckoutSessionId;

    /** SHA-256 hex hash of the current unauthenticated access token — the raw token is never stored. */
    @Column(name = "access_token_hash")
    private String accessTokenHash;

    @Column(name = "access_token_expires_at")
    private LocalDateTime accessTokenExpiresAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        this.plan = plan;
    }

    public LocalDateTime getTrialStartDate() {
        return trialStartDate;
    }

    public void setTrialStartDate(LocalDateTime trialStartDate) {
        this.trialStartDate = trialStartDate;
    }

    public LocalDateTime getTrialExpiryDate() {
        return trialExpiryDate;
    }

    public void setTrialExpiryDate(LocalDateTime trialExpiryDate) {
        this.trialExpiryDate = trialExpiryDate;
    }

    public TrialStatus getStatus() {
        return status;
    }

    public void setStatus(TrialStatus status) {
        this.status = status;
    }

    public int getLastReminderDayIndex() {
        return lastReminderDayIndex;
    }

    public void setLastReminderDayIndex(int lastReminderDayIndex) {
        this.lastReminderDayIndex = lastReminderDayIndex;
    }

    public PlanDuration getPlanDuration() {
        return planDuration;
    }

    public void setPlanDuration(PlanDuration planDuration) {
        this.planDuration = planDuration;
    }

    public ClassSlot getSelectedSlot() {
        return selectedSlot;
    }

    public void setSelectedSlot(ClassSlot selectedSlot) {
        this.selectedSlot = selectedSlot;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
    }

    public String getStripeCheckoutSessionId() {
        return stripeCheckoutSessionId;
    }

    public void setStripeCheckoutSessionId(String stripeCheckoutSessionId) {
        this.stripeCheckoutSessionId = stripeCheckoutSessionId;
    }

    public String getAccessTokenHash() {
        return accessTokenHash;
    }

    public void setAccessTokenHash(String accessTokenHash) {
        this.accessTokenHash = accessTokenHash;
    }

    public LocalDateTime getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }

    public void setAccessTokenExpiresAt(LocalDateTime accessTokenExpiresAt) {
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }
}
