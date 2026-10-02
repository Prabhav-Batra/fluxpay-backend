# ADR-006: UUIDv7 keys with prefixed public IDs

- **Status:** Accepted
- **Date:** 2026-10-02
- **Spec:** docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md

## Context
The ProjectOS PostgreSQL plugin requires UUID primary keys; a payments API is easier to use with typed IDs (prod_, cs_, sale_).

## Decision
Primary keys are UUIDv7. The API exposes them as a type prefix plus base62 of the UUID and decodes them at the boundary. A wrong prefix is treated as not found.

## Consequences
IDs are unguessable and self-describing. UUIDv7 reveals creation time, which is acceptable.
