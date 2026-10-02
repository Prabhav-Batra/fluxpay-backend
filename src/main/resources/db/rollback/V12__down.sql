DROP TABLE IF EXISTS webhook_deliveries;
DROP TABLE IF EXISTS webhook_endpoints;
DELETE FROM flyway_schema_history WHERE version = '12';
