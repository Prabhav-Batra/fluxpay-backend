# ADR-005: Tenant isolation and test/live modes

- **Status:** Accepted
- **Date:** 2026-10-02
- **Spec:** docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md

## Context
Many merchants share one database. Merchants need to integrate safely before taking real money.

## Decision
Every merchant-owned row carries merchant_id and mode. A TenantContext is built only from the authenticated principal (API key or dashboard session). Repositories expose only tenant-scoped finders. Cross-tenant lookups return 404. Each merchant has separate test and live keys, products and data.

## Consequences
No id-probing between tenants. Every new endpoint must be added to the tenant-isolation integration suite.
