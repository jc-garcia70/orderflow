
CREATE TABLE orders(
    id VARCHAR(255) PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    cancellation_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_orders_status CHECK ( status IN ('PENDING', 'CONFIRMED', 'CANCELLED'))
);

-- Used by the stale-order timeout job
CREATE INDEX idx_orders_status_created_at ON orders (status,created_at);

-- Used by GET /orders/user/{userId}
CREATE INDEX idx_orders_user_id ON orders(user_id);

CREATE TABLE order_items(
    id VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(12,2) NOT NULL,
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);

CREATE TABLE order_outbox(
    id VARCHAR(255) PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    message_key VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL,
    last_error VARCHAR(500)
);

CREATE INDEX idx_order_outbox_pending
    ON order_outbox (published_at, next_attempt_at, created_at);
