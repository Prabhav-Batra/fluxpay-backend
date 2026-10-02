CREATE TABLE products (
    id          UUID PRIMARY KEY,
    merchant_id UUID          NOT NULL REFERENCES merchants (id),
    mode        VARCHAR(4)    NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    name        VARCHAR(120)  NOT NULL,
    description VARCHAR(1000),
    image_url   VARCHAR(500),
    amount      BIGINT        NOT NULL CHECK (amount BETWEEN 100 AND 50000000),
    currency    VARCHAR(3)    NOT NULL CHECK (currency = 'INR'),
    type        VARCHAR(20)   NOT NULL CHECK (type = 'ONE_TIME'),
    metadata    JSONB         NOT NULL DEFAULT '{}',
    active      BOOLEAN       NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX products_merchant_mode_idx ON products (merchant_id, mode, id DESC);
