# PostgreSQL Best Practices

1. **Migrations**: Never alter schema via ad-hoc queries. Always use a migration tool (Flyway, Liquibase, Prisma) and store migrations in version control.
2. **Indexing**: Add B-Tree indexes to all Foreign Keys and columns used frequently in `WHERE` or `ORDER BY` clauses. Use `EXPLAIN ANALYZE` to prove index usage.
3. **Connection Pooling**: Do not open a new connection for every request. Use a connection pooler like PgBouncer or application-level pooling (HikariCP, Prisma Accelerate).
4. **Data Types**: Use specialized types when appropriate: `UUID` for identifiers, `JSONB` (not JSON text) for unstructured data, `TIMESTAMPTZ` (not TIMESTAMP) for global dates.
5. **Transactions**: Wrap multi-step writes (e.g., deducting balance AND recording ledger entry) in an explicit `BEGIN ... COMMIT` transaction block.
6. **Prepared Statements**: Always use parameterized queries or an ORM. Never concatenate user input directly into SQL strings to prevent SQL Injection.
7. **Pagination**: Avoid `OFFSET/LIMIT` for deep pagination on large tables, as it requires scanning and discarding rows. Use Keyset Pagination (Cursor Pagination) instead.
8. **Soft Deletes**: Consider adding a `deleted_at` timestamp rather than `DROP` or `DELETE` for critical business records to maintain referential integrity and audit trails.
9. **Constraint Enforcement**: Use database-level constraints (UNIQUE, CHECK, NOT NULL, FOREIGN KEY) to guarantee data integrity, even if the application layer has validation.
10. **JSONB Indexing**: If querying inside a JSONB column frequently, add a GIN index (`CREATE INDEX idx ON table USING GIN (jsonb_column)`).
