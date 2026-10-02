# Plan 3 — Merchant Webhooks, Analytics, Platform Admin

**Goal:** Merchants receive signed, retried webhooks for every outbox event and can manage endpoints, inspect deliveries and resend; the dashboard gets analytics numbers; the platform admin manages merchants, fees, suspension and payouts. After this plan the backend is feature-complete for v1.

**Spec:** `docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md` §7 (webhooks), §10 (analytics, admin), §5 (`webhook_endpoints`, `webhook_deliveries`, `payouts`). Builds on Plans 1–2.

**Constraints:** Plans 1–2 constraints apply. Ideas only here; code lives in the source files. Commit on `main` per task.

## Task 1 — Webhook endpoints and fan-out (module `events`)
- **Migration V12:**
  - `webhook_endpoints`: merchant, mode, url, secret, enabled, timestamps.
  - `webhook_deliveries`: event, endpoint, merchant, mode, status `PENDING/SUCCEEDED/FAILED`, attempt count, next attempt time, last status code and error, last attempt time.
  - Index on `(status, next_attempt_at)`.
- **Endpoint management:**
  - Dashboard routes `/api/v1/dashboard/webhook_endpoints`: list, create, PATCH url/enabled, DELETE, `POST /{id}/roll_secret`, `POST /{id}/test`.
  - The secret `whsec_…` is returned only on create and roll.
  - URL rules:
    - Live mode: `https` only, and the host must not resolve to a loopback, private, link-local or unspecified address (SSRF guard).
    - Test mode: `http://localhost` and `127.0.0.1` are also allowed, for local development.
  - At most 5 endpoints per mode → `422 ENDPOINT_LIMIT_REACHED`.
- **Fan-out:** `EventPublisher.publish` also inserts one `PENDING` delivery per enabled endpoint of that merchant and mode, in the same transaction.
- **Events API:**
  - `GET /api/v1/dashboard/events` (cursor) and `GET /{id}` (event plus its deliveries).
  - `POST /{id}/resend` creates new deliveries for enabled endpoints.
  - The test ping is event type `webhook.test`, delivered only to the chosen endpoint.
- **Tests:**
  - CRUD and validation, including the SSRF guard and the endpoint limit.
  - The secret is hidden in lists.
  - Publishing creates deliveries only for enabled endpoints of the same mode.
  - Resend and test ping.
  - Cross-tenant 404s.

## Task 2 — Delivery dispatcher
- **Claim:** in a short transaction, select due `PENDING` deliveries with `FOR UPDATE SKIP LOCKED`, at most 50. Push their `next_attempt_at` forward by a 2-minute lease so no other run takes them, then commit. Send outside any transaction, then record the result in another short transaction.
- **Request:**
  - POST JSON `{id, type, created, mode, data}`.
  - Header `FluxPay-Signature: t=<unix>,v1=<hex HMAC-SHA256(secret, t + "." + body)>`.
  - 10 s timeout, no redirects followed, and the SSRF guard is re-checked at send time.
- **Outcome:**
  - 2xx → `SUCCEEDED`.
  - Otherwise retry after 1m, 5m, 30m, 2h, 6h, 12h, 24h; after the 8th failed attempt → `FAILED`.
  - Store the status code and a truncated error.
  - A disabled or deleted endpoint → `FAILED` without sending.
- **Job:** `WebhookDispatchJob` runs every `fluxpay.jobs.webhook-interval` (10 s) when jobs are enabled. Tests call the service directly.
- **Tests:**
  - Unit: the signature format, and the retry schedule including its end.
  - Integration, against a JDK `HttpServer` on localhost in test mode:
    - A successful delivery, with the signature verified on the receiving side.
    - A 500 response schedules a retry.
    - A delivery becomes failed after the last attempt.
    - Leased deliveries are not claimed twice.
    - A disabled endpoint is not sent to.

## Task 3 — Analytics (module `analytics`, read-only SQL)
- **Routes:**
  - `GET /api/v1/dashboard/analytics/summary?from&to`: gross sales, refunds, platform fees, gateway fees, net, sales count.
  - `GET …/timeseries?from&to`: per-day revenue and sales count, with days bucketed in `fluxpay.analytics.timezone` (default `Asia/Kolkata`) and empty days filled with zero.
  - `GET …/top_products?from&to&limit`: product ID, units, revenue.
- **Ranges:** the default is the last 30 days. `from` must be before `to` and the span at most 366 days, otherwise `400 INVALID_RANGE`. Each request is scoped to its tenant and mode.
- **Tests:** totals after sales and a refund, day bucketing with zero-filled gaps, top-products order, range validation, other-mode and other-tenant isolation.

## Task 4 — Platform admin (module `admin`, plus payouts in `ledger`)
- **Migration V13:** `payouts`: merchant, mode, amount, reference (UTR), paid at, recorded by, created.
- **Ledger:** `recordPayout` writes the payout row and a `PAYOUT` ledger entry of −amount in one transaction. If the amount exceeds the available balance → `409 INSUFFICIENT_BALANCE`.
- **Admin routes** (`PLATFORM_ADMIN` only):
  - `GET /api/v1/admin/merchants` (cursor, with status and fee).
  - `GET /{id}` (test and live balances).
  - `PATCH /{id}` (`platform_fee_bps`, `status`).
  - `POST /{id}/payouts` and `GET /{id}/payouts`.
- **Behaviour:** a suspended merchant's API keys already return 403 `MERCHANT_SUSPENDED`. A fee change applies to sales recorded after it.
- **Tests:**
  - Admin list and detail.
  - A fee change affects the next sale.
  - Suspending blocks API keys.
  - A payout reduces the balance and is listed; an over-balance payout returns 409.
  - A merchant owner gets 403 on admin routes.
- **Close-out:** update ProjectOS features, tech debt and README, then do the final review.
