# ADR-003: Postgres outbox for merchant webhooks

- **Status:** Accepted
- **Date:** 2026-10-02
- **Spec:** docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md

## Context
Merchants must be told about every sale even if their endpoint is down, and a sale must never exist without its notification.

## Decision
Events and webhook deliveries are written in the same transaction as the sale. A scheduled dispatcher claims due deliveries with FOR UPDATE SKIP LOCKED and retries on a fixed backoff schedule (1m … 24h) before marking them failed.

## Consequences
At-least-once delivery; merchants dedupe by event id. No message broker to run. Throughput is bounded by the dispatcher, which is sufficient for v1.
