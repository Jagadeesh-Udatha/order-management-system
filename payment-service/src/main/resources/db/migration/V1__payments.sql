CREATE TABLE payment (
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    order_id    CHAR(36)      NOT NULL,
    customer_id VARCHAR(64)   NOT NULL,
    amount      DECIMAL(12,2) NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    gateway_ref VARCHAR(100)  NULL,
    reason      VARCHAR(255)  NULL,
    created_at  TIMESTAMP(6)  NOT NULL,
    updated_at  TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_payment_order UNIQUE (order_id)
);

CREATE TABLE outbox_event (
    id           CHAR(36)     NOT NULL PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(64)  NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMP(6) NOT NULL,
    published_at TIMESTAMP(6) NULL
);
CREATE INDEX idx_outbox_pending ON outbox_event (published_at, created_at);

CREATE TABLE processed_event (
    event_id     VARCHAR(64)  NOT NULL PRIMARY KEY,
    processed_at TIMESTAMP(6) NOT NULL
);
