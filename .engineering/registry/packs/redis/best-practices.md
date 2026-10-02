# Redis Best Practices

1. **TTL (Time to Live)**: Always set a TTL on cached items. Without an expiration, Redis will eventually run out of memory and crash or evict important keys.
2. **Key Naming Convention**: Use a consistent, hierarchical naming schema with colons as delimiters. (e.g., `app-name:domain:entity:id` -> `billing:users:profile:12345`).
3. **Appropriate Data Structures**: Redis is not just key-value. Use Hashes (`HSET`) for objects, Sets (`SADD`) for unique lists, and Sorted Sets (`ZADD`) for leaderboards or rate limiting.
4. **Pipelining**: If sending multiple commands in a row that do not depend on each other, use Pipelining to send them in a single network round-trip.
5. **Eviction Policies**: Configure the correct `maxmemory-policy`. Use `volatile-lru` if Redis is used purely as a cache, or `noeviction` if it's used as a primary datastore (e.g., job queues).
6. **Security**: Never expose Redis directly to the public internet. Bind it to localhost or a private VPC subnet. Enable TLS and AUTH passwords for production.
7. **Connection Pooling**: Like SQL databases, use a connection pool in your application to avoid the overhead of opening TCP connections for every cache hit.
8. **Rate Limiting**: Use Redis for distributed rate limiting across microservices using the Token Bucket or Sliding Window algorithms.
