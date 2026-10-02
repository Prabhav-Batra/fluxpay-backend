DROP TABLE IF EXISTS idempotency_keys;
DELETE FROM flyway_schema_history WHERE version = '8';
