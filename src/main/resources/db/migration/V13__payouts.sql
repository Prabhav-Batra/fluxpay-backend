CREATE TABLE payouts (
    id          UUID PRIMARY KEY,
    merchant_id UUID         NOT NULL REFERENCES merchants (id),
    mode        VARCHAR(4)   NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    amount      BIGINT       NOT NULL CHECK (amount > 0),
    currency    VARCHAR(3)   NOT NULL,
    reference   VARCHAR(100) NOT NULL,
    paid_at     TIMESTAMPTZ  NOT NULL,
    recorded_by UUID         NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX payouts_merchant_mode_idx ON payouts (merchant_id, mode, id DESC);
CREATE UNIQUE INDEX payouts_reference_uq ON payouts (merchant_id, mode, reference);
