DROP INDEX IF EXISTS checkout_sessions_reconcile_idx;
ALTER TABLE checkout_sessions DROP COLUMN IF EXISTS last_reconciled_at;
CREATE INDEX checkout_sessions_reconcile_idx ON checkout_sessions (created_at) WHERE gateway_order_id IS NOT NULL;
DELETE FROM flyway_schema_history WHERE version = '11';
