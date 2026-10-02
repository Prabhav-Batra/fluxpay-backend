# PostgreSQL Plugin

## Registration
- ID: `projectos.plugins.postgresql`
- Version: `1.0.0`
- Compatibility: `PostgreSQL 14+`

## Capabilities
- **Standards**: Injects PostgreSQL specific rules for schema design and query optimization.
- **Workflows**: Hooks into SQL migration tools (Flyway, Liquibase, Prisma).
- **Knowledge Packs**: Injects the `postgresql` tech pack from the registry.

## Core Rules
1. **UUIDs**: Use `uuid` (specifically v4 or v7) for primary keys instead of serial integers to avoid ID enumeration and ease distributed generation.
2. **Timestamps**: Always use `TIMESTAMP WITH TIME ZONE` (`timestamptz`). Never use `TIMESTAMP WITHOUT TIME ZONE`.
3. **JSONB**: Use `JSONB` for flexible payloads, but index the specific keys you query frequently using GIN indexes. Do not use JSONB to avoid proper relational modeling.
4. **Connection Pooling**: Always put a connection pooler (like PgBouncer) in front of the database to handle high concurrency.

## Common Anti-Patterns
- ❌ Using `SELECT *`. Always specify the exact columns needed to allow index-only scans and reduce memory.
- ❌ Leaving long-running transactions open, which prevents autovacuum from cleaning up dead tuples (causing bloat).
- ❌ Adding a column with a volatile `DEFAULT` value on a large table without taking locking into consideration.

## Dependencies
- Requires: PostgreSQL instance.
- Links to Pack: `registry/packs/postgresql/manifest.yaml`
