# Review Checklist: Database

> **Reviewer Instructions**: Validate schema migrations and query efficiency against `standards/database.md`.

## 🔴 Blockers (Must Fix)
- [ ] **Destructive Migrations**: Does the migration delete columns or tables without following the Expand-Contract pattern?
- [ ] **Missing Indexes**: Are all new foreign keys and frequently queried columns indexed?
- [ ] **SQL Injection Risk**: Are raw SQL queries constructed using string concatenation instead of parameterized inputs?
- [ ] **Transaction Scope**: Are transactions kept as short as possible? Do they span external network calls (they shouldn't)?
- [ ] **Schema Rollback**: Is the `DOWN` migration (rollback script) correct and perfectly symmetrical to the `UP` script?

## 🟡 Warnings (Should Fix)
- [ ] **N+1 Queries**: Do new ORM queries fetch relations in a loop rather than using eager loading/batching?
- [ ] **SELECT ***: Do queries fetch all columns when only a subset is needed?
- [ ] **Table Bloat**: Is a high-churn table lacking appropriate autovacuum tuning considerations?
- [ ] **Data Types**: Are the smallest appropriate data types used? (e.g., `VARCHAR(255)` vs `TEXT`, `SMALLINT` vs `INT`).

## 🟢 Advisories (Nice to Have)
- [ ] **Constraint Usage**: Are check constraints used to enforce data integrity at the DB level (e.g., `price > 0`)?
- [ ] **Soft Deletes**: Should this table use a `deleted_at` column instead of hard deletes?
