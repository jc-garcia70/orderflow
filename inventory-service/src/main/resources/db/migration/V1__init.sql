CREATE TABLE products (
    id VARCHAR(255) PRIMARY KEY,
    sku VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    price NUMERIC(12, 2) NOT NULL,
    available_quantity INTEGER NOT NULL,
    reserved_quantity  INTEGER NOT NULL,
    active BOOLEAN NOT NULL,
    version BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_products_sku UNIQUE (sku),
    -- Last line of defense: stock can never go negative, whatever the application does
    CONSTRAINT ck_products_quantities CHECK (available_quantity >= 0 AND reserved_quantity >= 0)
);

CREATE TABLE processed_order_reservations (
    order_id VARCHAR(36) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE stock_reservations (
    order_id VARCHAR(36) PRIMARY KEY,
    status VARCHAR(255) NOT NULL,
    version BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_stock_reservations_status
    CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED'))
);

CREATE TABLE stock_reservation_items (
    order_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,

    CONSTRAINT fk_reservation_items_reservation
        FOREIGN KEY (order_id) REFERENCES stock_reservations (order_id)
);

CREATE INDEX idx_reservation_items_order_id ON stock_reservation_items (order_id);

CREATE TABLE inventory_outbox (
    id VARCHAR(36) PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    message_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL,
    last_error VARCHAR(500)
);

CREATE INDEX idx_inventory_outbox_pending
    ON inventory_outbox (published_at, next_attempt_at, created_at);