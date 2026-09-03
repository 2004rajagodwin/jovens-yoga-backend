CREATE TABLE class_slots (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    slot_date           DATE            NOT NULL,
    start_time          TIME            NOT NULL,
    end_time            TIME,
    capacity            INT,
    label               VARCHAR(150),
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    display_order       INT             NOT NULL DEFAULT 0,
    created_at          DATETIME        NOT NULL,
    updated_at          DATETIME        NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
