CREATE TABLE customer_order (
    id              CHAR(36)      NOT NULL PRIMARY KEY,
    customer_id     VARCHAR(64)   NOT NULL,
    idempotency_key VARCHAR(64)   NOT NULL,
    request_hash    VARCHAR(64)      NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    status_reason   VARCHAR(255)  NULL,
    total           DECIMAL(12,2) NOT NULL,
    created_at      TIMESTAMP(6)  NOT NULL,
    updated_at      TIMESTAMP(6)  NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_order_idempotency UNIQUE (customer_id, idempotency_key)
);
CREATE INDEX idx_order_customer_created ON customer_order (customer_id, created_at);
CREATE INDEX idx_order_status_updated ON customer_order (status, updated_at);

CREATE TABLE order_line (
    order_id     CHAR(36)      NOT NULL,
    line_no      INT           NOT NULL,
    product_id   CHAR(36)      NOT NULL,
    product_name VARCHAR(255)  NOT NULL,
    quantity     INT           NOT NULL,
    unit_price   DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (order_id, line_no),
    CONSTRAINT fk_line_order FOREIGN KEY (order_id) REFERENCES customer_order (id)
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
