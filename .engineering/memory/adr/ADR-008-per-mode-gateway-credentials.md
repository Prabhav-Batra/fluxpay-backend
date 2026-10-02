# ADR-008: Per-mode Razorpay credentials and webhook URLs

- **Status:** Accepted
- **Date:** 2026-10-02
- **Spec:** docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md §3, §6, §8

## Context
Razorpay test and live modes have separate API keys and separate webhook configurations and secrets. FluxPay sessions carry a mode.

## Decision
Credentials are configured per mode (`RAZORPAY_TEST_*` required, `RAZORPAY_LIVE_*` optional and all-or-nothing). Each mode has its own webhook URL, `/api/v1/gateway-webhooks/razorpay/{test|live}`, verified with that mode's secret. Orders for a session are created with the session's mode credentials. Integration tests replace the gateway with an in-memory fake that signs webhooks with the same HMAC scheme.

## Consequences
Live mode can stay disabled until go-live (`GATEWAY_NOT_CONFIGURED`). A webhook can never be accepted for the wrong mode. Two webhook entries must be configured in the Razorpay dashboard.
