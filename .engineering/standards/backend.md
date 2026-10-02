# Global Standard: Backend

## Architecture
1. **Statelessness**: All application instances must be stateless. Session state must be stored in external data stores (Redis, database). This enables horizontal scaling and zero-downtime deploys.
2. **Separation of Concerns**: Enforce strict boundaries between presentation (Controllers), business logic (Services), and data access (Repositories). Controllers must never contain business logic. Services must never return HTTP status codes.
3. **Dependency Injection**: All dependencies must be injected, never instantiated inside business logic. This enables testability and loose coupling.
4. **Interface segregation**: Depend on abstractions, not implementations. Service classes should implement interfaces. Repository classes should implement interfaces.

## Request Handling
5. **Fail-fast validation**: Validate all inputs at the boundary. Fail immediately with a meaningful HTTP status code and structured error response. Never let invalid data reach the service layer.
6. **Idempotent mutations**: All PUT and DELETE operations must be idempotent. POST operations that create resources should use idempotency keys to prevent duplicates.
7. **Request correlation**: Every incoming request must be assigned a unique correlation ID (or propagate the existing one) for distributed tracing.
8. **Request/Response DTOs**: Never expose domain entities directly via API responses. Always map to DTOs. This decouples internal schema evolution from the API contract.

## Data Access
9. **Repository pattern**: Data access must be abstracted behind repository interfaces. Business logic must never contain raw SQL or ORM-specific query syntax.
10. **Connection pooling**: Database connections must use connection pools with configurable min/max sizes. Never open ad-hoc connections.
11. **Transaction boundaries**: Transactions must be scoped at the service layer, not the repository layer. Keep transactions as short as possible.
12. **N+1 query prevention**: Use eager loading, batch fetching, or projections to prevent N+1 query patterns. Monitor query counts in development.

## Resilience
13. **Circuit breakers**: All calls to external services must be wrapped in circuit breakers with configurable failure thresholds and recovery periods.
14. **Timeouts**: Every external call (HTTP, gRPC, database, cache) must have an explicit timeout. Never use infinite timeouts.
15. **Retry with backoff**: Retries on transient failures must use exponential backoff with jitter. Cap at 3 retries maximum.
16. **Graceful shutdown**: On SIGTERM, the service must stop accepting new requests, drain in-flight requests, close connections, and exit cleanly.

## Configuration
17. **Environment-based config**: Configuration must be loaded from environment variables or config files, never hardcoded. Follow the 12-Factor App methodology.
18. **Feature flags**: New features behind flags must default to OFF. Flag evaluation must be fast (cached) and must not block the request path.

## Health & Observability
19. **Health endpoints**: Every service must expose `/health/live` (liveness) and `/health/ready` (readiness) endpoints. Readiness must verify database and critical dependency connectivity.
20. **Structured logging**: All logs must be structured JSON with fields: `timestamp`, `level`, `message`, `correlationId`, `service`, `traceId`.
