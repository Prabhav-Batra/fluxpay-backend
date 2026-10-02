# Global Standard: Database

## Schema Design
1. **Third Normal Form (3NF) by default**: Normalize schemas to 3NF unless there is a documented, measured performance reason to denormalize. Document the trade-off in an ADR.
2. **Naming conventions**: Tables are plural snake_case (`user_wallets`). Columns are singular snake_case (`created_at`). Primary keys are `id`. Foreign keys are `{referenced_table_singular}_id` (e.g., `user_id`).
3. **Audit columns**: Every table must include `created_at` (timestamp, NOT NULL, DEFAULT NOW), `updated_at` (timestamp, NOT NULL), and optionally `deleted_at` (timestamp, nullable) for soft deletes.
4. **UUID over auto-increment**: Use UUIDs for primary keys in distributed systems. Auto-increment IDs are acceptable for single-database monoliths but must never be exposed in public APIs.
5. **Explicit constraints**: Every column must declare NOT NULL unless nullability is intentional. Use CHECK constraints for enums and value ranges. Never rely on application-level validation alone.

## Indexes
6. **Index every foreign key**: All foreign key columns must be indexed. This prevents full table scans on JOINs and cascade deletes.
7. **Composite index order**: In composite indexes, place the most selective column first. Match the index order to your most common query WHERE clause order.
8. **No unused indexes**: Review index usage quarterly. Drop indexes with zero reads. Every unused index slows down writes.

## Migrations
9. **Forward-only migrations**: Every schema change must be a new migration file, never an edit to an existing one. Migrations must be idempotent (safe to re-run).
10. **Expand-Contract pattern**: For breaking changes (rename column, change type), use a 3-phase approach: (1) add new column, (2) backfill and dual-write, (3) drop old column. Never do destructive changes in a single migration.
11. **Migration naming**: `V{version}__{description}.sql`. Example: `V014__add_wallet_currency_column.sql`.
12. **No data in migrations**: Migrations handle schema only. Seed data and backfills are separate scripts with their own idempotency.

## Queries
13. **Parameterized queries only**: All queries must use parameterized statements or ORM-generated queries. Never concatenate user input into SQL strings.
14. **Pagination required**: All queries that return lists must support pagination. Use cursor-based pagination for large tables.
15. **Explain before shipping**: Every new query on tables with > 10K rows must be validated with EXPLAIN ANALYZE before merging to production.
16. **Connection pooling**: Always use connection pools. Configure min/max pool sizes per environment. Monitor pool exhaustion.

## Backups & Recovery
17. **Backup verification**: Backups are not real until they've been successfully restored. Test restore procedures monthly.
18. **Point-in-time recovery**: Production databases must support PITR with at least 7-day retention.
