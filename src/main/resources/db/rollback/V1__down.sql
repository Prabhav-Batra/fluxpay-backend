-- Manual rollback for V1 (Flyway community edition does not run undo scripts).
DROP TABLE IF EXISTS spring_session_attributes;
DROP TABLE IF EXISTS spring_session;
DELETE FROM flyway_schema_history WHERE version = '1';
