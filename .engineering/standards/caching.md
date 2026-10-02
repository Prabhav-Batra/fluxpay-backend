# Global Standard: Caching

## Strategies
1. **Cache-Aside (Lazy Loading)**: Use as the default strategy. The application checks the cache; if a miss occurs, it fetches from the database, updates the cache, and returns the data.
2. **Write-Through**: Use for data that must be immediately available in the cache after a write. The application writes to the cache and the database in a single transaction.
3. **TTL (Time to Live)**: Every cached item must have an explicit TTL. Never cache data indefinitely. Set TTL based on the data's volatility and business tolerance for staleness.
4. **Cache Invalidation**: Hard invalidation is notoriously difficult. Prefer short TTLs over complex event-driven invalidation logic unless real-time consistency is strictly required.

## Key Design
5. **Namespacing**: Cache keys must be namespaced to prevent collisions and allow partial clears. Format: `namespace:entity:id` (e.g., `users:profile:12345`).
6. **Versioning**: Include a version identifier in the cache key or namespace if the data schema changes frequently (e.g., `v2:users:profile:12345`).

## Performance & Resilience
7. **Thundering Herd prevention**: If a highly trafficked cache key expires, use a mutex or probabilistic early expiration to prevent multiple processes from simultaneously querying the database to rebuild the cache.
8. **Cache failure handling**: The application must gracefully degrade if the cache layer (e.g., Redis) is unavailable. Catch cache exceptions and fall back to the primary database.
9. **Serialization**: Use efficient serialization formats (e.g., Protobuf, MessagePack) for large cached objects to reduce memory usage and network serialization overhead. JSON is acceptable for small payloads.
10. **Avoid caching large collections**: Do not cache massive lists or result sets as a single key. Cache individual items and use sets/lists in the cache store to manage collections.
