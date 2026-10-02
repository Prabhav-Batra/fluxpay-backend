# Plan 2 — Catalog, Checkout, Razorpay Payments, Sales, Ledger

**Status:** Implemented on `main` (commits `36db773`…`f43de18`). This document describes the ideas; the code lives in the source files.

**Goal:** A merchant creates products and payment links; its backend creates a checkout session with an API key (or a customer opens a payment link); the customer pays through Razorpay; FluxPay verifies Razorpay's signed webhook, records exactly one sale with ledger entries (gross, platform fee, gateway fee), writes a `checkout.completed` event to the outbox, and handles refunds, expiry and missed webhooks.

**Spec:** `docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md` — §4, §5, §6, §8, §9.

**Architecture:** New modules `catalog`, `events`, `ledger`, `payments`, `checkout`, `sales`, plus `common.idempotency`, `common.pagination`, `common.validation`, `common.web.RedirectUrlPolicy`. Module dependencies follow `.engineering/config/architecture.yaml`. `payments` never depends on `sales`: it exposes a `GatewayEventHandler` interface that `sales` implements (`GatewayEventRouter`).

## Global constraints

- Plan 1's constraints still apply (layering, `FluxpayException`, snake_case JSON — ADR-007, prefixed IDs, tenant + mode from the principal, ProjectOS limits, Spotless, Conventional Commits, work on `main`).
- Tests run on PostgreSQL 18 (Neon's version).
- Money is `long` paise, `INR` only; product amount ₹1–₹5,00,000.
- Platform fee = gross × `platform_fee_bps` / 10000, rounded half up, computed by the ledger.
- New tenant repositories extend Spring Data `Repository` and expose only tenant-scoped finders (plus gateway-ID lookups for the webhook flow).
- Methods that must join a caller's transaction use `Propagation.MANDATORY` (event publish, ledger writes, session lock/complete/expire, payment record).
- No Razorpay HTTP call runs inside a database transaction.
- Razorpay credentials per mode (ADR-008): `RAZORPAY_TEST_*` required, `RAZORPAY_LIVE_*` optional and all-or-nothing; webhook URL per mode `/api/v1/gateway-webhooks/razorpay/{test|live}`.
- Lists take `limit` (1–100, default 20) and `starting_after`, return `{data, cursor, has_more}`, newest first (UUIDv7 order).

## Review focus (all covered by tests)

1. Duplicate `payment.captured` deliveries, sequential or concurrent → one sale, one set of ledger entries, one event.
2. Payment after expiry → sale still recorded, session completed.
3. Amount mismatch → no sale, payment flagged; repricing a product never changes an existing session's price.
4. Double-clicked Pay → one attached Razorpay order, both responses agree.
5. Same `Idempotency-Key` with a different body → 409; same body (even with metadata keys reordered) → original response.

---

## Tasks

### 1. Pagination, metadata, PostgreSQL 18
- Switch Testcontainers to `postgres:18-alpine`.
- `PageQuery` turns `starting_after` + `limit` into a cursor (`before()` = cursor or max UUID, `fetchSize()` = limit + 1); bad input → 400 `INVALID_LIMIT` / `INVALID_CURSOR`.
- `CursorPage.from(rows, query, mapper, cursorOf)` trims the extra row and sets `has_more` / `cursor`.
- `Metadata.validated` — ≤ 20 keys, key `[A-Za-z0-9_.-]{1,40}`, value ≤ 500 chars, 422 with codes `METADATA_TOO_MANY_KEYS` / `METADATA_INVALID_KEY` / `METADATA_INVALID_VALUE`.
- `PublicId.parseOrNotFound` for path IDs.
- Tests: PageQuery, CursorPage, Metadata unit tests; whole Plan 1 suite on PG 18.

### 2. Products
- Table `products` (V4): merchant, mode, name, description, image URL, amount (100–50,000,000), currency `INR`, type `ONE_TIME`, `metadata` jsonb, active, timestamps.
- `ProductService`: create, get (404 `PRODUCT_NOT_FOUND` across tenant or mode), list with optional `active` filter, partial update (null = unchanged, empty description/image clears it; archive = `active: false`).
- Dashboard routes `/api/v1/dashboard/products[/{id}]` (GET/POST/PATCH); API-key read routes `/api/v1/products[/{id}]`.
- Tests: defaults, paging with cursor, update/archive/filter, 422 cases (amount < 100, USD, bad metadata, http image), 400 bad cursor/limit, API key sees only its mode.

### 3. Payment links and redirect policy
- `RedirectUrlPolicy.validate(mode, field, url)`: absolute URL without user-info; `https` always, `http://localhost|127.0.0.1` only in test mode; ≤ 2048 chars. Codes `INVALID_URL`, `INSECURE_URL`, `URL_TOO_LONG`.
- Table `payment_links` (V5) with a random 10-char base62 `slug`, optional success/cancel URLs, active flag. Link URL = `{FRONTEND_BASE_URL}/l/{slug}`.
- `PaymentLinkService`: create (product must belong to tenant + mode), get, list, update (URLs, active), `findActiveBySlug` for the public flow.
- Tests: policy unit tests; link creation, deactivate + list, foreign/other-mode product → 404, insecure URL → 422.

### 4. Event outbox (write side)
- Table `events` (V6): merchant, mode, type, `data` jsonb.
- `EventType`: `checkout.completed`, `checkout.expired`, `sale.refunded`.
- `EventPublisher.publish` is MANDATORY, so an event exists only if the change that caused it commits. Delivery to merchants comes in Plan 3.
- Tests: stored inside a transaction; refuses without one; rolled back with its transaction.

### 5. Ledger
- Table `ledger_entries` (V7): signed amounts by type `SALE_GROSS`, `PLATFORM_FEE`, `GATEWAY_FEE`, `REFUND`, `PAYOUT`; optional `sale_id`, `reference` (unique per type when present — refund idempotency).
- `FeeCalculator.platformFee` (half up). `LedgerService.recordSale` writes gross / −platform fee / −gateway fee; `recordRefund` writes −amount once per gateway refund ID; `balance` sums by type; `entries` pages.
- Dashboard `GET /api/v1/dashboard/balance` and `/ledger_entries` (`le_` IDs).
- Tests: fee rounding edge cases; sale entries and balance; refund once; writes need a transaction; dashboard per mode.

### 6. Razorpay gateway adapter
- `PaymentGateway` interface (name, enabled per mode, public key, create order, fetch order payments, verify webhook signature).
- `RazorpayGateway`: `RestClient` with connect/read timeouts (`razorpayRestClient` bean), Basic auth per mode, maps failures to 502 `GATEWAY_UNAVAILABLE`, missing mode credentials to `GATEWAY_NOT_CONFIGURED`. Signature = HMAC-SHA256 of the raw body with the mode's webhook secret, constant-time compare.
- `RazorpayWebhookParser` → `PaymentCaptured` / `RefundProcessed` / `Ignored`; malformed → 400 `MALFORMED_WEBHOOK`.
- `RazorpayProperties` fails startup on missing test credentials or partially-set live credentials.
- Test support: `FakePaymentGateway` (`@Primary`, signs with a fixed secret, configurable delay/failure, injectable payments) and `TestWebhooks` (Razorpay-shaped bodies + signature).
- Tests: properties, order creation request shape and auth, failure mapping, unconfigured live mode, payment parsing, signature checks, parser cases.

### 7. Idempotency keys
- Table `idempotency_keys` (V8) keyed by merchant + mode + key, storing a request hash and the response JSON.
- `IdempotencyService.execute(tenant, key, request, type, action)`: claims the key before running (so a concurrent duplicate gets 409 `IDEMPOTENCY_REQUEST_IN_PROGRESS`), replays the stored response for the same request, 409 `IDEMPOTENCY_KEY_REUSED` for a different one, releases the key if the action fails, 400 for blank/over-long keys. Runs outside transactions. The request hash uses key-sorted JSON (review fix).
- Tests: replay, reuse conflict, in-progress, release on failure, no key, invalid key, cleanup, reordered metadata.

### 8. Checkout sessions
- Table `checkout_sessions` (V9): product, optional payment link, **price snapshot**, `customer_ref`, URLs, metadata, status `OPEN/COMPLETED/EXPIRED`, unique `gateway_order_id`, `expires_at` (TTL `fluxpay.checkout.session-ttl`, 30 min).
- Merchant API: `POST /api/v1/checkout_sessions` (Idempotency-Key aware; product must be active → 409 `PRODUCT_INACTIVE`; URLs per policy) and `GET /{id}`; response includes hosted `url` = `{FRONTEND_BASE_URL}/pay/{cs_id}`.
- Public API: `GET /api/v1/public/checkout_sessions/{id}` (product + merchant branding; shows `expired` once past expiry; `success_url` only when completed), `POST …/{id}/pay` (409 `SESSION_COMPLETED` / `SESSION_EXPIRED` / `MERCHANT_UNAVAILABLE`; creates the Razorpay order outside a transaction, attaches it with a conditional update, losers re-read), `POST /api/v1/public/payment_links/{slug}/checkout_sessions` with optional `ref`.
- Service methods for later tasks: lock by gateway order (FOR UPDATE), mark completed, expire due, find reconcilable, mark reconciled.
- Tests: creation, snapshot after repricing, archived/unknown/other-mode product, redirect policy per mode, idempotency, other-mode 404; public view, one reused order, concurrent double pay, expired session, gateway failure 502, link → session with ref, inactive link 404.

### 9. Capture processing
- Tables `payments` and `sales` (V10). Payment status `CAPTURED`, `AMOUNT_MISMATCH`, `DUPLICATE`; sale status `PAID`, `PARTIALLY_REFUNDED`, `REFUNDED`.
- `RazorpayWebhookController` at `/api/v1/gateway-webhooks/razorpay/{mode}` (public POST): unknown mode → 404, bad signature → 400 `INVALID_SIGNATURE`, parse, dispatch to `GatewayEventHandler`, reply `{received: true}`.
- `SaleCaptureService.capture`, in one transaction: skip if the payment is already recorded → lock the session by order ID → re-check under the lock → wrong mode ignored → completed session ⇒ record `DUPLICATE` → amount/currency mismatch ⇒ record `AMOUNT_MISMATCH` → otherwise record `CAPTURED`, complete the session (also from `EXPIRED`), create the sale from the snapshot, write ledger entries, publish `checkout.completed` (`sale_id, checkout_session_id, product_id, customer_ref, amount, currency, metadata`).
- Tests: happy path (sale, ledger 4900/−245/−116, event payload, success URL exposed), sequential and concurrent duplicates, after expiry, amount mismatch, repricing, second payment flagged duplicate, signature/mode rejections, unknown order and ignored events acknowledged.

### 10. Refunds
- `SaleRefundService.refund` in one transaction: unknown payment → 409 `PAYMENT_NOT_YET_RECORDED` so Razorpay redelivers (review fix); flagged or other-mode payment → acknowledged and ignored; otherwise lock the sale, record the ledger refund once per refund ID, update refunded amount and status, publish `sale.refunded` (`refund_amount`, `amount_refunded`, `status`). Platform fee is kept (TD-002).
- Tests: partial then full refund with ledger and events, repeated webhook applied once, refund before capture asks for retry and applies after the capture arrives.

### 11. Sales queries
- `SalesQueryService` with JPA Specifications (tenant, cursor, `customer_ref`, `product_id`, `status`) and `SaleDetailView` (sale + payment method, gateway payment ID, gateway fee).
- API key: `GET /api/v1/sales`, `GET /api/v1/sales/{id}`. Dashboard: `GET /api/v1/dashboard/sales` (adds `status`), `GET /api/v1/dashboard/sales/{id}` (adds `payment`). Errors `INVALID_PRODUCT_ID`, `INVALID_STATUS`, `SALE_NOT_FOUND`.
- Tests: by customer newest first, product filter + paging, detail with payment, status filter after refund, bad filters.

### 12. Jobs and public rate limits
- `fluxpay.jobs.*` (`JobsProperties`): enabled flag (`FLUXPAY_JOBS_ENABLED`, false in tests), intervals, reconciliation min age (10 min) and grace after expiry (1 h), idempotency retention (24 h). `SchedulingConfig` and job beans exist only when enabled — run jobs on one instance.
- `CheckoutExpiryService`: expires up to 100 due sessions and emits `checkout.expired` each, in one transaction.
- `ReconciliationService`: for sessions with an order, older than the min age and not more than the grace past expiry, **least-recently reconciled first** (V11 `last_reconciled_at`, review fix), fetch Razorpay payments outside any transaction and capture the captured ones; one failure does not stop the batch; every checked session is marked reconciled.
- `IdempotencyCleanupJob` deletes keys past retention hourly.
- Rate-limit rules: public POST 30/min/IP, public GET 300/min/IP.
- Tests: expiry once + event, valid sessions untouched, missed capture recovered once, recent/uncaptured skipped, paid session reached despite 100 abandoned ones.

### 13. Isolation, records, smoke test
- Tenant-isolation tests for products (dashboard + API), selling/linking another merchant's product, sessions, sales, balance; verified to fail when product scoping is removed.
- ADR-007 (snake_case JSON), ADR-008 (per-mode gateway credentials); features, tech debt (TD-007…TD-010) and README Razorpay setup updated.
- Real-Razorpay smoke test against a throwaway local Postgres: everything up to order creation works; Razorpay returned 401 for the legacy test keys (TD-010 — re-run with current keys).

## Outcome

195 tests green. Final review: 0 critical, 3 important (fixed with failing-first tests), 7 minor (TD-009).
