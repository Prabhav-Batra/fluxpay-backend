# PostgreSQL Pitfalls

1. ❌ **N+1 Queries**: Fetching a list of 50 users, and then executing 50 separate queries to fetch each user's address. Use `JOIN` or batch loading (DataLoader) instead.
2. ❌ **SELECT \***: Using `SELECT *` in production code. It fetches unnecessary data, slowing down network transfer, and breaks if column definitions change. Select only the exact columns needed.
3. ❌ **Missing Foreign Keys**: Storing an ID that points to another table without defining an actual Foreign Key constraint. This leads to orphaned records.
4. ❌ **Long-Running Transactions**: Keeping a transaction open while waiting for external network calls (e.g., calling Stripe API). This locks rows and degrades database throughput.
5. ❌ **UUID v4 as Clustered Index (MySQL/SQL Server caveat applied to PG)**: While Postgres handles UUIDs better than others, random UUIDs can cause index bloat. Consider UUID v7 if sequential ordering is needed.
6. ❌ **Unindexed Foreign Keys**: Forgetting to index the foreign key column. When deleting a parent record, Postgres must do a full table scan on the child table to enforce referential integrity.
7. ❌ **Storing Passwords in Plaintext**: Never store raw passwords. Use strong hashing algorithms like Argon2 or bcrypt.
8. ❌ **Silent Data Truncation**: Relying on the application to prevent string length overflow rather than setting strict `VARCHAR(N)` or `CHECK` constraints on the table.
