CREATE TABLE admins (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(150)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    role            VARCHAR(30)     NOT NULL,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME        NOT NULL,
    updated_at      DATETIME        NOT NULL,
    CONSTRAINT uq_admins_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name          VARCHAR(100)    NOT NULL,
    last_name           VARCHAR(100)    NOT NULL,
    email               VARCHAR(255)    NOT NULL,
    country_region      VARCHAR(100),
    country_phone_code  VARCHAR(10)     NOT NULL,
    mobile_number       VARCHAR(30)     NOT NULL,
    address             VARCHAR(500),
    created_at          DATETIME        NOT NULL,
    updated_at          DATETIME        NOT NULL,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_mobile_number UNIQUE (mobile_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE plans (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(150)    NOT NULL,
    description         TEXT,
    image_url           VARCHAR(500),
    plan_type           VARCHAR(30)     NOT NULL,
    currency            VARCHAR(8)      NOT NULL,
    trial_duration_days INT,
    featured            BOOLEAN         NOT NULL DEFAULT FALSE,
    badge_text          VARCHAR(100),
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    display_order       INT             NOT NULL DEFAULT 0,
    created_at          DATETIME        NOT NULL,
    updated_at          DATETIME        NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE plan_durations (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id             BIGINT          NOT NULL,
    duration_label      VARCHAR(100)    NOT NULL,
    duration_value      INT             NOT NULL,
    duration_unit       VARCHAR(20)     NOT NULL,
    price               DECIMAL(12,2)   NOT NULL,
    currency            VARCHAR(8)      NOT NULL,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    display_order       INT             NOT NULL DEFAULT 0,
    created_at          DATETIME        NOT NULL,
    updated_at          DATETIME        NOT NULL,
    CONSTRAINT fk_plan_durations_plan FOREIGN KEY (plan_id) REFERENCES plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE plan_features (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id             BIGINT          NOT NULL,
    feature_text        VARCHAR(500)    NOT NULL,
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    display_order       INT             NOT NULL DEFAULT 0,
    created_at          DATETIME        NOT NULL,
    updated_at          DATETIME        NOT NULL,
    CONSTRAINT fk_plan_features_plan FOREIGN KEY (plan_id) REFERENCES plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE trials (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                 BIGINT          NOT NULL,
    plan_id                 BIGINT          NOT NULL,
    trial_start_date        DATETIME        NOT NULL,
    trial_expiry_date       DATETIME        NOT NULL,
    status                  VARCHAR(30)     NOT NULL,
    last_reminder_day_index INT             NOT NULL DEFAULT 0,
    created_at              DATETIME        NOT NULL,
    updated_at              DATETIME        NOT NULL,
    CONSTRAINT uq_trials_user UNIQUE (user_id),
    CONSTRAINT fk_trials_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_trials_plan FOREIGN KEY (plan_id) REFERENCES plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE orders (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number                VARCHAR(50)     NOT NULL,
    user_id                     BIGINT          NOT NULL,
    plan_id                     BIGINT          NOT NULL,
    plan_duration_id            BIGINT          NOT NULL,
    plan_name_snapshot          VARCHAR(150)    NOT NULL,
    duration_label_snapshot     VARCHAR(100)    NOT NULL,
    amount                      DECIMAL(12,2)   NOT NULL,
    currency                    VARCHAR(8)      NOT NULL,
    status                      VARCHAR(30)     NOT NULL,
    stripe_checkout_session_id  VARCHAR(255),
    created_at                  DATETIME        NOT NULL,
    updated_at                  DATETIME        NOT NULL,
    CONSTRAINT uq_orders_order_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_plan FOREIGN KEY (plan_id) REFERENCES plans (id),
    CONSTRAINT fk_orders_plan_duration FOREIGN KEY (plan_duration_id) REFERENCES plan_durations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id                    BIGINT          NOT NULL,
    stripe_payment_intent_id    VARCHAR(255),
    stripe_checkout_session_id  VARCHAR(255),
    stripe_event_id             VARCHAR(255),
    amount                      DECIMAL(12,2)   NOT NULL,
    currency                    VARCHAR(8)      NOT NULL,
    status                      VARCHAR(30)     NOT NULL,
    paid_at                     DATETIME,
    created_at                  DATETIME        NOT NULL,
    updated_at                  DATETIME        NOT NULL,
    CONSTRAINT uq_payments_stripe_event_id UNIQUE (stripe_event_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notifications (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT,
    type                VARCHAR(30)     NOT NULL,
    channel             VARCHAR(20)     NOT NULL,
    reference_type      VARCHAR(50),
    reference_id        BIGINT,
    event_key           VARCHAR(255)    NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    provider_reference  VARCHAR(255),
    failure_reason      TEXT,
    sent_at             DATETIME,
    created_at          DATETIME        NOT NULL,
    updated_at          DATETIME        NOT NULL,
    CONSTRAINT uq_notifications_event_key UNIQUE (event_key),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
