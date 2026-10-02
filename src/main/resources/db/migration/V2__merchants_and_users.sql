CREATE TABLE merchants (
    id               UUID PRIMARY KEY,
    business_name    VARCHAR(100) NOT NULL,
    slug             VARCHAR(60)  NOT NULL UNIQUE,
    logo_url         VARCHAR(500),
    brand_color      VARCHAR(7),
    platform_fee_bps INTEGER      NOT NULL CHECK (platform_fee_bps BETWEEN 0 AND 10000),
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(30)  NOT NULL CHECK (role IN ('MERCHANT_OWNER', 'PLATFORM_ADMIN')),
    merchant_id   UUID REFERENCES merchants (id),
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT users_role_merchant CHECK ((role = 'MERCHANT_OWNER') = (merchant_id IS NOT NULL))
);

CREATE INDEX users_merchant_idx ON users (merchant_id);
