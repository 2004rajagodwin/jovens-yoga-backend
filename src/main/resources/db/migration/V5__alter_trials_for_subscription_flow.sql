-- Reworks the free-trial flow into a paid-subscription-with-trial-period flow: a trial now
-- links to a specific plan duration and class slot, and is only ACTIVE once Stripe confirms
-- the subscription checkout, so trial_start_date/trial_expiry_date must be nullable while a
-- trial sits in TRIAL_PENDING_PAYMENT.

ALTER TABLE trials ADD COLUMN plan_duration_id BIGINT NULL AFTER plan_id;
ALTER TABLE trials ADD COLUMN selected_slot_id BIGINT NULL AFTER plan_duration_id;
ALTER TABLE trials ADD COLUMN stripe_customer_id VARCHAR(255) NULL;
ALTER TABLE trials ADD COLUMN stripe_subscription_id VARCHAR(255) NULL;
ALTER TABLE trials ADD COLUMN stripe_checkout_session_id VARCHAR(255) NULL;

ALTER TABLE trials MODIFY COLUMN trial_start_date DATETIME NULL;
ALTER TABLE trials MODIFY COLUMN trial_expiry_date DATETIME NULL;

ALTER TABLE trials ADD CONSTRAINT fk_trials_plan_duration FOREIGN KEY (plan_duration_id) REFERENCES plan_durations (id);
ALTER TABLE trials ADD CONSTRAINT fk_trials_selected_slot FOREIGN KEY (selected_slot_id) REFERENCES class_slots (id);
