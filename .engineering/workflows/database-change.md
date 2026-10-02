# Workflow: Database Change

## 1. Required Inputs
- The target schema change (e.g., add column, alter index).
- Data migration strategy (if altering existing data).

## 2. Required Context
- Current database schema and ERD.
- `standards/database.md`

## 3. Required Reviews
- Database Review (`reviews/database.md`)
- Code Review (`reviews/code.md`)

## 4. Expected Outputs
- A versioned migration script (e.g., Flyway, Liquibase, or Prisma migration).
- Updated ORM or DAO models in the application code.

## 5. Exit Criteria
- Migration executes successfully on an empty staging database.
- Migration rolls back successfully (if supported by the stack).

## 6. Execution Steps
1. **Schema Diffing**: Analyze the target state against the current state. Identify if the change is destructive (e.g., `DROP COLUMN`).
2. **Expand-Contract Verification**: If the change is destructive, enforce the Expand-Contract pattern. The first PR must only Add the new column/table.
3. **Migration Script Creation**: Write the `.sql` or ORM migration script. Ensure it contains the `UP` and `DOWN` (rollback) directives.
4. **Index Optimization Check**: Verify that any new foreign keys have accompanying B-Tree indexes.
5. **ORM Sync**: Update the application-level data models (e.g., JPA Entities, Prisma models) to match the new database schema.
6. **Staging Dry-Run**: Execute the migration on a local or staging database replica. Run the application test suite against the migrated database.
