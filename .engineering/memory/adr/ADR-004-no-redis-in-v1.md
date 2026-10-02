# ADR-004: No Redis in v1

- **Status:** Accepted
- **Date:** 2026-10-02
- **Spec:** docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md

## Context
The ProjectOS stack lists Redis. v1 runs a single backend instance; sessions, rate limiting and the outbox can all live in Postgres or in-process.

## Decision
Dashboard sessions are stored in Postgres (Spring Session JDBC). Rate limiting uses in-process Bucket4j buckets. Redis is not a v1 dependency.

## Consequences
One less paid service and failure point. When a second instance is added, rate limiting must move to a shared store (TD-003).
