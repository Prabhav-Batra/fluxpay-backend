# ADR-007: snake_case JSON everywhere

- **Status:** Accepted
- **Date:** 2026-10-02
- **Spec:** docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md §4, §9

## Context
ProjectOS `standards/api.md` shows camelCase examples (`traceId`, `hasMore`). The spec's merchant API is Stripe-style (`product_id`, `customer_ref`, `success_url`).

## Decision
All JSON — requests, responses, error envelopes and webhook payloads — uses snake_case via the global Jackson naming strategy. The error envelope field is `trace_id`; list responses use `has_more`.

## Consequences
One convention for merchants to learn. Deviates from the ProjectOS example field names; the ProjectOS rule (consistent envelope, cursor pagination) is otherwise followed.
