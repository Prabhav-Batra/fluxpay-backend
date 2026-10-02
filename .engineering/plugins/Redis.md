# Redis Plugin

## Registration
- ID: `projectos.plugins.redis`
- Version: `1.0.0`
- Compatibility: `Redis 6+`

## Capabilities
- **Standards**: Injects Redis-specific rules for caching, data structures, and eviction.
- **Knowledge Packs**: Injects the `redis` tech pack from the registry.

## Core Rules
1. **Eviction Policies**: Always configure `maxmemory` and an eviction policy (e.g., `volatile-lru` or `allkeys-lru`). Never leave Redis without a memory limit in production.
2. **Key Namespacing**: Use colons to namespace keys (e.g., `user:1000:session`). This allows easy logical grouping and scanning.
3. **Pipelining**: When executing multiple independent commands, use pipelining to send them in a single network round-trip.
4. **Data Structures**: Use the appropriate data structure (Hashes for objects, Sets for unique collections, Sorted Sets for leaderboards/time-series). Don't stringify large JSON objects if you only need to update a single field.

## Common Anti-Patterns
- ❌ Running `KEYS *` in production. It blocks the single-threaded event loop. Use `SCAN` instead.
- ❌ Using Redis as a primary database without understanding AOF/RDB persistence trade-offs and risks of data loss.
- ❌ Storing huge values (multi-megabyte strings) which can cause latency spikes during allocation and deallocation.

## Dependencies
- Requires: Redis instance.
- Links to Pack: `registry/packs/redis/manifest.yaml`
