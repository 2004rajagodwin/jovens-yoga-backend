-- Free trials are now activated against a STANDARD/PREMIUM plan's subscription checkout
-- (with a trial period), so standalone FREE_TRIAL plans are no longer purchasable.
UPDATE plans SET active = FALSE WHERE plan_type = 'FREE_TRIAL';
