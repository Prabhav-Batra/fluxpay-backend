CREATE TABLE events (
    id          UUID PRIMARY KEY,
    merchant_id UUID        NOT NULL REFERENCES merchants (id),
    mode        VARCHAR(4)  NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    type        VARCHAR(40) NOT NULL,
    data        JSONB       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX events_merchant_mode_idx ON events (merchant_id, mode, id DESC);
