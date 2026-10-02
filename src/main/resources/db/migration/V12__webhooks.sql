CREATE TABLE webhook_endpoints (
    id          UUID PRIMARY KEY,
    merchant_id UUID          NOT NULL REFERENCES merchants (id),
    mode        VARCHAR(4)    NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    url         VARCHAR(2048) NOT NULL,
    secret      VARCHAR(64)   NOT NULL,
    enabled     BOOLEAN       NOT NULL,
    deleted_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX webhook_endpoints_merchant_mode_idx ON webhook_endpoints (merchant_id, mode) WHERE deleted_at IS NULL;

CREATE TABLE webhook_deliveries (
    id               UUID PRIMARY KEY,
    event_id         UUID         NOT NULL REFERENCES events (id),
    endpoint_id      UUID         NOT NULL REFERENCES webhook_endpoints (id),
    merchant_id      UUID         NOT NULL REFERENCES merchants (id),
    mode             VARCHAR(4)   NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    status           VARCHAR(20)  NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    attempt_count    INTEGER      NOT NULL,
    next_attempt_at  TIMESTAMPTZ,
    last_status_code INTEGER,
    last_error       VARCHAR(500),
    last_attempt_at  TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX webhook_deliveries_due_idx ON webhook_deliveries (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX webhook_deliveries_event_idx ON webhook_deliveries (event_id);
