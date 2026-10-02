CREATE TABLE api_keys (
    id           UUID PRIMARY KEY,
    merchant_id  UUID        NOT NULL REFERENCES merchants (id),
    mode         VARCHAR(4)  NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    lookup_id    VARCHAR(16) NOT NULL UNIQUE,
    secret_hash  VARCHAR(64) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ
);

CREATE INDEX api_keys_merchant_mode_idx ON api_keys (merchant_id, mode, created_at DESC);
