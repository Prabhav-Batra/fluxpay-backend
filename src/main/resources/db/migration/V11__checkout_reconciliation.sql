ALTER TABLE checkout_sessions ADD COLUMN last_reconciled_at TIMESTAMPTZ;

DROP INDEX IF EXISTS checkout_sessions_reconcile_idx;
CREATE INDEX checkout_sessions_reconcile_idx ON checkout_sessions (last_reconciled_at NULLS FIRST, created_at)
    WHERE gateway_order_id IS NOT NULL AND status IN ('OPEN', 'EXPIRED');
