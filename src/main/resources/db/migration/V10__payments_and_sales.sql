CREATE TABLE payments (
    id                  UUID PRIMARY KEY,
    merchant_id         UUID          NOT NULL REFERENCES merchants (id),
    mode                VARCHAR(4)    NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    checkout_session_id UUID          NOT NULL REFERENCES checkout_sessions (id),
    gateway             VARCHAR(20)   NOT NULL,
    gateway_payment_id  VARCHAR(100)  NOT NULL UNIQUE,
    gateway_order_id    VARCHAR(100)  NOT NULL,
    status              VARCHAR(30)   NOT NULL CHECK (status IN ('CAPTURED', 'AMOUNT_MISMATCH', 'DUPLICATE')),
    amount              BIGINT        NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    method              VARCHAR(30),
    gateway_fee         BIGINT        NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL
);

CREATE INDEX payments_session_idx ON payments (checkout_session_id);

CREATE TABLE sales (
    id                  UUID PRIMARY KEY,
    merchant_id         UUID          NOT NULL REFERENCES merchants (id),
    mode                VARCHAR(4)    NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    product_id          UUID          NOT NULL REFERENCES products (id),
    checkout_session_id UUID          NOT NULL UNIQUE REFERENCES checkout_sessions (id),
    payment_id          UUID          NOT NULL UNIQUE REFERENCES payments (id),
    customer_ref        VARCHAR(255),
    amount              BIGINT        NOT NULL,
    refunded_amount     BIGINT        NOT NULL DEFAULT 0,
    currency            VARCHAR(3)    NOT NULL,
    status              VARCHAR(20)   NOT NULL CHECK (status IN ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')),
    metadata            JSONB         NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL
);

CREATE INDEX sales_merchant_mode_idx ON sales (merchant_id, mode, id DESC);
CREATE INDEX sales_merchant_mode_customer_idx ON sales (merchant_id, mode, customer_ref);
CREATE INDEX sales_merchant_mode_product_idx ON sales (merchant_id, mode, product_id);
