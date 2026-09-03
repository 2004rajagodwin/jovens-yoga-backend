INSERT INTO plans (id, name, description, plan_type, currency, trial_duration_days, featured, badge_text, active, display_order, created_at, updated_at)
VALUES
    (1, 'Free Trial', 'Try Jovens Yoga free for 5 days. No payment required.', 'FREE_TRIAL', 'USD', 5, FALSE, NULL, TRUE, 0, NOW(), NOW()),
    (2, 'Standard', 'Everything you need for a consistent home yoga practice.', 'STANDARD', 'USD', NULL, FALSE, NULL, TRUE, 1, NOW(), NOW()),
    (3, 'Premium', 'Our complete wellness experience with live sessions and personal coaching.', 'PREMIUM', 'USD', NULL, TRUE, 'Best choice', TRUE, 2, NOW(), NOW());

INSERT INTO plan_durations (plan_id, duration_label, duration_value, duration_unit, price, currency, active, display_order, created_at, updated_at)
VALUES
    (1, '5 Days', 5, 'DAY', 0.00, 'USD', TRUE, 0, NOW(), NOW()),
    (2, 'Per Month', 1, 'MONTH', 29.00, 'USD', TRUE, 0, NOW(), NOW()),
    (2, 'Per Year', 12, 'MONTH', 290.00, 'USD', TRUE, 1, NOW(), NOW()),
    (3, 'Per Month', 1, 'MONTH', 59.00, 'USD', TRUE, 0, NOW(), NOW()),
    (3, 'Per Year', 12, 'MONTH', 590.00, 'USD', TRUE, 1, NOW(), NOW());

INSERT INTO plan_features (plan_id, feature_text, active, display_order, created_at, updated_at)
VALUES
    (1, 'Full access to all class libraries', TRUE, 0, NOW(), NOW()),
    (1, 'Beginner-friendly guided sessions', TRUE, 1, NOW(), NOW()),
    (1, 'No credit card required', TRUE, 2, NOW(), NOW()),
    (2, 'Unlimited on-demand classes', TRUE, 0, NOW(), NOW()),
    (2, 'Weekly new content', TRUE, 1, NOW(), NOW()),
    (2, 'Progress tracking', TRUE, 2, NOW(), NOW()),
    (3, 'Everything in Standard', TRUE, 0, NOW(), NOW()),
    (3, 'Live instructor-led sessions', TRUE, 1, NOW(), NOW()),
    (3, '1-on-1 coaching call monthly', TRUE, 2, NOW(), NOW()),
    (3, 'Priority support', TRUE, 3, NOW(), NOW());
