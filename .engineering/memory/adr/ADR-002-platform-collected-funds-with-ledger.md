# ADR-002: Platform-collected funds with a per-merchant ledger

- **Status:** Accepted
- **Date:** 2026-10-02
- **Spec:** docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md

## Context
The first tenant (Jextter) has no payment gateway of its own. Merchants need money tracked per tenant, and automatic splits (Razorpay Route) are a later phase.

## Decision
Phase 1 collects all payments in one platform Razorpay account configured by environment. Every sale writes append-only ledger entries (gross, platform fee, gateway fee). Payouts are recorded manually by a platform admin. Platform fee is configured per merchant in basis points.

## Consequences
Balances are always derivable from the ledger. Phase 2 swaps manual payouts for Razorpay Route without changing the ledger. Operating funds for third parties at scale requires Route or a PA licence — tracked in tech debt.
