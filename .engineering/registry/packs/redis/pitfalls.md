# Redis Pitfalls

1. ❌ **Using KEYS in Production**: Never run the `KEYS *` command in a production environment. It blocks the single-threaded Redis engine until it completes. Use `SCAN` instead.
2. ❌ **Massive Payloads**: Storing multi-megabyte JSON blobs in a single key. Redis is optimized for small, fast reads. Chunk large data or store it in an object store (S3).
3. ❌ **Treating Redis as Persistent Storage**: Unless explicitly configured for AOF/RDB persistence with synchronous writes (which degrades performance), assume data in Redis can be lost if the server restarts.
4. ❌ **Race Conditions (Check-and-Set)**: Reading a value, modifying it in the application, and writing it back. If two threads do this, data is lost. Use Lua scripts or `INCR`/`DECR` for atomic operations.
5. ❌ **OOM (Out of Memory) Crashes**: Failing to monitor memory usage or set a `maxmemory` limit. The OS will forcefully kill the Redis process if it consumes all RAM.
6. ❌ **Storing Passwords/PII Unencrypted**: Caching sensitive data in plaintext. If Redis is dumped or compromised, the data is exposed. Always encrypt PII before caching.
