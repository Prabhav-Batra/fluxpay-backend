CREATE TABLE checkout_sessions (
    id               UUID PRIMARY KEY,
    merchant_id      UUID          NOT NULL REFERENCES merchants (id),
    mode             VARCHAR(4)    NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    product_id       UUID          NOT NULL REFERENCES products (id),
    payment_link_id  UUID          REFERENCES payment_links (id),
    amount           BIGINT        NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    customer_ref     VARCHAR(255),
    success_url      VARCHAR(2048),
    cancel_url       VARCHAR(2048),
    metadata         JSONB         NOT NULL DEFAULT '{}',
    status           VARCHAR(20)   NOT NULL CHECK (status IN ('OPEN', 'COMPLETED', 'EXPIRED')),
    gateway_order_id VARCHAR(100)  UNIQUE,
    expires_at       TIMESTAMPTZ   NOT NULL,
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL
);

CREATE INDEX checkout_sessions_merchant_mode_idx ON checkout_sessions (merchant_id, mode, id DESC);
CREATE INDEX checkout_sessions_status_expires_idx ON checkout_sessions (status, expires_at);
CREATE INDEX checkout_sessions_reconcile_idx ON checkout_sessions (created_at) WHERE gateway_order_id IS NOT NULL;
