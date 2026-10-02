DROP TABLE IF EXISTS sales;
DROP TABLE IF EXISTS payments;
DELETE FROM flyway_schema_history WHERE version = '10';
