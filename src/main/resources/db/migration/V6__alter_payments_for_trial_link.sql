-- Payments can now originate from either a one-off order or a trial subscription invoice,
-- so order_id becomes optional and a new nullable trial_id link is added. Whether a payment
-- must have exactly one of order/trial is enforced in application code, not via a DB CHECK
-- constraint (portability risk across MySQL versions).

ALTER TABLE payments MODIFY COLUMN order_id BIGINT NULL;
ALTER TABLE payments ADD COLUMN trial_id BIGINT NULL AFTER order_id;
ALTER TABLE payments ADD CONSTRAINT fk_payments_trial FOREIGN KEY (trial_id) REFERENCES trials (id);
