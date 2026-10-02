CREATE TABLE ledger_entries (
    id          UUID PRIMARY KEY,
    merchant_id UUID         NOT NULL REFERENCES merchants (id),
    mode        VARCHAR(4)   NOT NULL CHECK (mode IN ('TEST', 'LIVE')),
    type        VARCHAR(20)  NOT NULL CHECK (type IN ('SALE_GROSS', 'PLATFORM_FEE', 'GATEWAY_FEE', 'REFUND', 'PAYOUT')),
    amount      BIGINT       NOT NULL,
    currency    VARCHAR(3)   NOT NULL,
    sale_id     UUID,
    reference   VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ledger_entries_merchant_mode_idx ON ledger_entries (merchant_id, mode, id DESC);
CREATE INDEX ledger_entries_sale_idx ON ledger_entries (sale_id);
CREATE UNIQUE INDEX ledger_entries_type_reference_uq ON ledger_entries (type, reference) WHERE reference IS NOT NULL;
