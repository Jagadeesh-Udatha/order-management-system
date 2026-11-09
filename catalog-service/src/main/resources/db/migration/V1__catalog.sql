CREATE TABLE product (
    id            CHAR(36)      NOT NULL PRIMARY KEY,
    sku           VARCHAR(64)   NOT NULL,
    name          VARCHAR(255)  NOT NULL,
    category      VARCHAR(100)  NOT NULL,
    price         DECIMAL(12,2) NOT NULL,
    available_qty INT           NOT NULL,
    reserved_qty  INT           NOT NULL DEFAULT 0,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    version       BIGINT        NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_product_sku UNIQUE (sku),
    CONSTRAINT ck_product_stock CHECK (available_qty >= 0 AND reserved_qty >= 0)
);
CREATE INDEX idx_product_category_name ON product (category, name);

CREATE TABLE reservation (
    order_id   CHAR(36)     NOT NULL PRIMARY KEY,
    status     VARCHAR(20)  NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE reservation_item (
    order_id   CHAR(36) NOT NULL,
    product_id CHAR(36) NOT NULL,
    quantity   INT      NOT NULL,
    CONSTRAINT fk_item_reservation FOREIGN KEY (order_id) REFERENCES reservation (order_id)
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
