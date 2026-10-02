CREATE TABLE idempotency_keys (
    merchant_id     UUID         NOT NULL REFERENCES merchants (id),
    mode            VARCHAR(4)   NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (merchant_id, mode, idempotency_key)
);

CREATE INDEX idempotency_keys_created_idx ON idempotency_keys (created_at);
