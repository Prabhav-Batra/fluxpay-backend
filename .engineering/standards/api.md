# Global Standard: API Design

## Resource Design
1. **RESTful by default**: Unless otherwise specified in `architecture.yaml`, APIs must use strict RESTful resource naming. Use nouns (`/users`, `/wallets`), never verbs (`/getUser`, `/createWallet`).
2. **Plural resource names**: Collections must be plural (`/users`, not `/user`). Singleton sub-resources are singular (`/users/{id}/profile`).
3. **Nested resources for ownership**: Use nesting to express ownership (`/users/{userId}/orders/{orderId}`). Limit nesting to 2 levels maximum.
4. **HTTP methods as verbs**: GET (read), POST (create), PUT (full replace), PATCH (partial update), DELETE (remove). Never use POST for retrieval.

## Versioning
5. **URL-based versioning**: All public APIs must be versioned via URL prefix (`/api/v1/resource`). Never use header-based versioning for external APIs.
6. **Breaking change policy**: Removing a field, changing a field type, or removing an endpoint is a breaking change requiring a major version bump. Adding optional fields is non-breaking.
7. **Deprecation window**: Deprecated API versions must remain available for at least 6 months with a `Sunset` header and migration guide.

## Request / Response
8. **Idempotency**: All PUT and DELETE requests must be mathematically idempotent. POST requests for critical operations (payments, transfers) must accept an `Idempotency-Key` header.
9. **Pagination**: All list endpoints must support cursor-based pagination. Response format: `{ data: [], cursor: "...", hasMore: true }`. Never use offset-based pagination for large datasets.
10. **Filtering and sorting**: Support query parameters for filtering (`?status=active`) and sorting (`?sort=createdAt:desc`). Document allowed filter/sort fields.
11. **Partial responses**: Support field selection via `?fields=id,name,email` for bandwidth-sensitive clients.

## Error Handling
12. **Standard error envelope**: All errors must use a consistent structure:
    ```json
    { "error": { "code": "WALLET_INSUFFICIENT_FUNDS", "message": "...", "details": [], "traceId": "..." } }
    ```
13. **HTTP status codes**: Use correct codes. 400 (bad input), 401 (unauthenticated), 403 (unauthorized), 404 (not found), 409 (conflict), 422 (validation), 429 (rate limited), 500 (server error). Never return 200 with an error body.
14. **Validation errors**: Return 422 with field-level detail: `{ "field": "email", "code": "INVALID_FORMAT", "message": "..." }`.

## Documentation
15. **OpenAPI spec**: Every API must have an OpenAPI 3.0+ specification. The spec is the source of truth; code must match it.
16. **Example payloads**: Every endpoint in the spec must include at least one request and response example.

## Security
17. **Authentication required by default**: All endpoints require authentication unless explicitly marked as public in the spec.
18. **Rate limiting**: All public endpoints must declare their rate limit in response headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`).
