# Scenario: Add Redis Caching to Product Catalog

**Domain**: E-Commerce
**Difficulty**: Medium Risk (Performance)

## Description
The `/api/v1/products` endpoint is experiencing high latency during flash sales. We need to introduce a Redis caching layer to memoize frequent product queries.

## Expected Workflow
- `workflows/refactor.md`
- Needs `reviews/performance.md` and `reviews/architecture.md`

## Exit Criteria
- Cache hit ratio metrics are emitted.
- Data consistency is maintained (cache invalidation on product update).
- Latency drops by 50%.
