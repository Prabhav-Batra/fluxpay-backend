CREATE TABLE payment_links (
    id          UUID PRIMARY KEY,
    merchant_id UUID          NOT NULL REFERENCES merchants (id),
    mode        VARCHAR(4)    NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    product_id  UUID          NOT NULL REFERENCES products (id),
    slug        VARCHAR(32)   NOT NULL UNIQUE,
    success_url VARCHAR(2048),
    cancel_url  VARCHAR(2048),
    active      BOOLEAN       NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX payment_links_merchant_mode_idx ON payment_links (merchant_id, mode, id DESC);
