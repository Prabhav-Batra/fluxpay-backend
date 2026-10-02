# FluxPay v1 — Design Spec

**Date:** 2026-10-02
**Status:** Draft — awaiting review
**Repos:** `fluxpay-backend` (Spring Boot), `fluxpay-frontend` (Next.js). `fluxpay-webhook-worker` and `fluxpay-customer-frontend` are archived.
**Governance:** ProjectOS (`.engineering/`) is installed in both active repos; its standards and constraints apply.

---

## 1. Goal

FluxPay is a multi-tenant B2B SaaS that lets websites sell **one-time digital products** (credit packs, lives, unlocks) without building payments themselves. A merchant signs up, creates products, sends its users to a FluxPay hosted checkout, and receives a signed webhook saying *"user X bought product Y"*. FluxPay records every sale and shows the merchant revenue and sales analytics.

**First tenant:** Jextter (coin packs ₹19 / ₹49 / ₹99 / ₹499; tournament lives ₹10). Jextter integrates exactly like any outside client — nothing about it is hardcoded.

### Success criteria
1. A new merchant can sign up, create a product, get API keys, register a webhook URL, and complete a test-mode payment end to end without help.
2. Every captured payment produces exactly one sale, one set of ledger entries, and at least one delivered (or visibly failed) webhook.
3. No merchant can read or change another merchant's data through any endpoint (enforced by tests).
4. All configuration (gateway keys, fees, URLs, secrets) comes from environment or database — none in code.

### Out of scope for v1
Subscriptions, merchant KYC, automated payouts (Razorpay Route), refunds initiated from FluxPay, multiple currencies, teams/multiple users per merchant, coupons, invoices/GST documents, credit balances (FluxPay never tracks credits — merchants map products to credits themselves).

---

## 2. Money flow (Phase 1)

- All payments are collected into **one platform Razorpay account** (FluxPay's), configured via environment variables.
- Every sale writes **ledger entries** per merchant: gross, platform fee, gateway fee. The merchant's balance owed = sum of its ledger entries.
- The platform admin pays merchants out manually and records each payout in FluxPay.
- **Phase 2 (not in v1):** Razorpay Route linked accounts automate splits and settlements. The ledger model does not change; only the payout step does. The gateway sits behind a `PaymentGateway` interface so this is additive.

Compliance notes (business decisions recorded, not enforced by code): Jextter is operated as a brand of the Fluxpay MSME and is disclosed on the Razorpay account; Jextter coins/lives are non-redeemable for cash.

---

## 3. Architecture

```
Merchant backend ──(sk_ API key)──▶ fluxpay-backend ──▶ Razorpay (orders API)
        ▲                                 │   ▲
        │ signed webhook (outbox)         │   │ Razorpay webhooks (payment.captured, refund.processed)
        └─────────────────────────────────┘   │
End user ──▶ fluxpay-frontend /pay/*, /l/* ───┘ (Razorpay Checkout popup)
Merchant ──▶ fluxpay-frontend /dashboard/*
Platform admin ──▶ fluxpay-frontend /admin/*
```

### 3.1 Backend (`fluxpay-backend`)
Single Spring Boot 3.5 / Java 21 application (modular monolith), Gradle (Kotlin DSL), PostgreSQL 17, Flyway. No Redis in v1 (ProjectOS stack lists it; it is not needed until horizontal scaling — recorded as an ADR).

Package-by-feature under `com.fluxpay`, each module layered `api` (controllers + DTOs) → `service` (interfaces + impls) → `domain` (entities, value objects) → `persistence` (repositories):

| Module | Responsibility | May depend on |
|---|---|---|
| `common` | Money, IDs (prefixed), error envelope, clock, correlation ID, pagination | — |
| `identity` | Dashboard users, signup/login, sessions, platform-admin role | common |
| `merchants` | Merchant profile, branding, fee config, tenant context | common, identity |
| `apikeys` | Key issue/hash/roll/revoke, API-key authentication | common, merchants |
| `catalog` | Products, payment links | common, merchants |
| `checkout` | Checkout sessions, expiry, public session view | common, merchants, catalog, payments |
| `payments` | `PaymentGateway` interface, Razorpay adapter, inbound gateway webhooks, reconciliation | common |
| `sales` | Sale record creation on capture, refunds | common, checkout, ledger, events |
| `ledger` | Ledger entries, balances, payouts | common, merchants |
| `events` | Event outbox, webhook endpoints, delivery + retries, signing | common, merchants |
| `analytics` | Read-only aggregate queries | common, sales, ledger |
| `admin` | Platform-admin endpoints | merchants, ledger |

Dependency direction is enforced by an ArchUnit test. ProjectOS constraints: controllers ≤ 300 lines, services ≤ 500 lines, one public class per file, constructor injection, `@ConfigurationProperties` for config, DTOs never entities in responses.

### 3.2 Frontend (`fluxpay-frontend`)
Next.js 15 (App Router), React 19, TypeScript, Tailwind 4, shadcn/ui, TanStack Query + Table, react-hook-form + zod, Recharts, pnpm.

Route groups: `(auth)` signup/login · `dashboard/*` merchant console · `admin/*` platform admin · `pay/[sessionId]` and `l/[slug]` hosted checkout (public, mobile-first). Feature folders (`src/features/<feature>/{components,api,hooks,schemas}`), one component per file.

---

## 4. Merchant integration

### 4.1 Checkout Session API (primary)
```
POST /api/v1/checkout_sessions          Authorization: Bearer sk_test_…   Idempotency-Key: <uuid>
{ "product_id": "prod_…", "customer_ref": "u_123",
  "success_url": "https://jextter.com/paid", "cancel_url": "https://jextter.com/store",
  "metadata": { "any": "string" } }
→ 201 { "id": "cs_…", "url": "https://<frontend>/pay/cs_…", "status": "open", "expires_at": "…" }
```
Merchant redirects the user to `url`. Price and `customer_ref` are fixed server-side.

### 4.2 Payment Links (no-code)
Merchant creates a link in the dashboard → `https://<frontend>/l/<slug>`. Optional `?ref=<customer_ref>`. Opening it creates a checkout session for the link's product and redirects to `/pay/cs_…`. Success/cancel URLs come from the link config.

### 4.3 Public merchant API (v1)
`checkout_sessions` (create, retrieve), `products` (list, retrieve), `sales` (list by `customer_ref`, retrieve). All under `/api/v1`, all API-key authenticated, all scoped to the key's merchant + mode.

---

## 5. Data model

Rules: every merchant-owned row has `merchant_id` + `mode` (`test`|`live`); tenant and mode come only from the authenticated principal; cross-tenant lookups return **404**. Money is `bigint` paise + `currency` (`INR` only in v1). Primary keys are UUIDv7 (ProjectOS PostgreSQL plugin); the API exposes them as prefixed IDs (`prod_`, `plink_`, `cs_`, `pay_`, `sale_`, `evt_`, `we_`, `po_`) + base62 of the UUID, decoded at the boundary. All timestamps `timestamptz` UTC.

| Table | Key columns |
|---|---|
| `users` | id, email (unique), password_hash (bcrypt), role (`merchant_owner`/`platform_admin`), merchant_id (nullable for admin) |
| `merchants` | id, business_name, slug (unique), logo_url, brand_color, platform_fee_bps, status (`active`/`suspended`) |
| `api_keys` | id, merchant_id, mode, prefix, secret_hash (SHA-256), last_used_at, revoked_at |
| `products` | id, merchant_id, mode, name, description, image_url, amount, currency, type (`one_time`), metadata jsonb, active |
| `payment_links` | id, merchant_id, mode, product_id, slug (unique), success_url, cancel_url, active |
| `checkout_sessions` | id, merchant_id, mode, product_id, amount, currency (snapshot), customer_ref, success_url, cancel_url, metadata, status (`open`/`completed`/`expired`), gateway_order_id, payment_link_id, expires_at |
| `payments` | id, checkout_session_id, gateway, gateway_payment_id (unique), status, amount, method, gateway_fee |
| `sales` | id, merchant_id, mode, product_id, checkout_session_id, payment_id, customer_ref, amount, currency, status (`paid`/`refunded`), created_at |
| `ledger_entries` | id, merchant_id, mode, sale_id/payout_id, type (`sale_gross`/`platform_fee`/`gateway_fee`/`refund`/`payout`), amount (signed), created_at — append-only |
| `payouts` | id, merchant_id, amount, reference (UTR), paid_at, recorded_by |
| `webhook_endpoints` | id, merchant_id, mode, url, secret, enabled |
| `events` | id, merchant_id, mode, type, payload jsonb, created_at |
| `webhook_deliveries` | id, event_id, endpoint_id, status (`pending`/`succeeded`/`failed`), attempt_count, next_attempt_at, last_status_code, last_error |
| `idempotency_keys` | merchant_id, key, request_hash, response, created_at (24h TTL) |

Indexes: `(merchant_id, mode, created_at)` on `sales`, `ledger_entries`, `events`; `(status, next_attempt_at)` on `webhook_deliveries`; `(status, expires_at)` on `checkout_sessions`.

---

## 6. Payment lifecycle

1. **Session created** (API or link) → `open`, price snapshot taken, expires in 30 min.
2. **Checkout page** loads `GET /api/v1/public/checkout_sessions/{id}` (product name, price, merchant branding only). On "Pay", backend creates a Razorpay order (amount from snapshot, `notes.session_id`), stores `gateway_order_id`, returns order id + public key id; frontend opens Razorpay Checkout.
3. **Razorpay `payment.captured` webhook** → verify signature (constant-time) → in one transaction: insert `payments` (unique gateway id ⇒ duplicates are no-ops), check amount == snapshot (mismatch ⇒ flag, no sale), mark session `completed`, insert `sales`, insert ledger entries (gross, −platform fee = gross × bps / 10000 rounded half-up, −gateway fee from Razorpay), insert `events` row `checkout.completed` + one `webhook_deliveries` row per enabled endpoint.
4. **Frontend** polls session status after the popup closes; on `completed` redirects to `success_url?session_id=cs_…`.
5. **Reconciliation job** (every 5 min): for `open` sessions with an order older than 10 min, fetch order payments from Razorpay and run step 3 if captured.
6. **Expiry job**: `open` sessions past `expires_at` with no captured payment → `expired`, emit `checkout.expired`.
7. **Refund** (done in Razorpay dashboard) → `refund.processed` webhook → sale `refunded`, ledger `refund` entry (−amount), emit `sale.refunded`. Platform fee is not returned in v1 (recorded as tech debt / policy decision).

---

## 7. Outgoing webhooks

- Events: `checkout.completed`, `checkout.expired`, `sale.refunded`.
- Body: `{ "id": "evt_…", "type": "...", "created": <unix>, "mode": "live", "data": { "sale_id", "checkout_session_id", "product_id", "customer_ref", "amount", "currency", "metadata" } }`.
- Header `FluxPay-Signature: t=<unix>,v1=<hex HMAC-SHA256(endpoint_secret, t + "." + raw_body)>`. Merchants reject timestamps older than 5 min and dedupe by `id`.
- Dispatcher job polls due deliveries (`FOR UPDATE SKIP LOCKED`), POSTs with 10 s timeout. 2xx ⇒ `succeeded`. Otherwise reschedule at 1m, 5m, 30m, 2h, 6h, 12h, 24h; after the last attempt ⇒ `failed`.
- Dashboard shows events and attempts; merchant can resend (creates a new delivery for the same event) and send a test event.

---

## 8. Security

- **API auth:** `Authorization: Bearer sk_{test|live}_<random>`; lookup by prefix, compare SHA-256 hash in constant time. Key shown once at creation.
- **Dashboard auth:** email + password (bcrypt), server session (Spring Session JDBC, ADR-004) in an httpOnly, Secure, SameSite=Lax cookie; CSRF token on state-changing requests. Platform-admin endpoints require `platform_admin`.
- **Tenant isolation:** a `TenantContext` built from the principal; repositories only expose methods that take `merchantId` + `mode`. Integration suite attempts cross-tenant access on every endpoint and expects 404.
- **Inbound gateway webhooks:** signature verified; unsigned/invalid ⇒ 400 and logged.
- **Redirect URLs:** must be `https://` in live mode; `http://localhost` allowed in test mode.
- **Rate limits:** in-process token buckets (Bucket4j) on login, signup, public checkout and link routes; headers `X-RateLimit-*` per ProjectOS API standard. (Moves to Redis when there is more than one instance — tech debt.)
- **CORS:** only the configured frontend origin.
- **Secrets:** env only; app fails to start if `DATABASE_URL`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`, `SESSION_SECRET`, `FRONTEND_BASE_URL` are missing.
- **Schema:** Flyway only; `ddl-auto: validate`.
- Logging: structured JSON with correlation ID; never log secrets, full card data, or API keys.

---

## 9. Errors

Envelope (ProjectOS `standards/api.md`): `{ "error": { "code": "PRODUCT_NOT_FOUND", "message": "…", "details": [], "traceId": "…" } }`. Status codes: 400 bad input, 401 no/invalid auth, 403 forbidden role, 404 not found (incl. cross-tenant), 409 conflict / idempotency mismatch, 422 field validation (`details: [{ field, code, message }]`), 429 rate limited, 502 gateway error, 500 unexpected. `POST` endpoints accept `Idempotency-Key`; replays return the stored response, mismatched bodies return 409. List endpoints use cursor pagination `{ data, cursor, hasMore }`.

---

## 10. Dashboard, analytics, checkout UI

- **Dashboard:** test/live toggle; Home (KPIs: gross revenue, net revenue, sales count, units per product for today/7d/30d/custom; revenue-over-time chart; top products; recent sales); Products; Payment links; Sales (filters, search by `customer_ref`, detail with payment + webhook history); Balance (ledger + payouts); Developers (API keys, webhook endpoints, events log, quick-start snippet); Settings (business name, logo, brand colour).
- **Analytics:** SQL aggregates over `sales` / `ledger_entries` using the indexes above; no separate analytics store.
- **Admin:** merchants list, edit `platform_fee_bps`, suspend, balances, record payout.
- **Hosted checkout:** merchant logo/colour, product image/name/price, Pay button → Razorpay popup; states: ready, processing, success (redirecting), expired, failed (retry). Mobile-first.

---

## 11. Testing

- **Backend unit:** money and fee math, ID generation, webhook signing, retry schedule, idempotency, status transitions (`should_x_when_y` naming, AAA).
- **Backend integration:** Testcontainers PostgreSQL, MockMvc against every endpoint; Razorpay stubbed at HTTP boundary (WireMock) with recorded payloads; tenant-isolation suite; ArchUnit module-boundary test.
- **Frontend:** Vitest + Testing Library for forms and key components; one Playwright E2E: signup → product → session via API → pay (Razorpay test mode) → sale visible → webhook received by a local receiver.
- CI: GitHub Actions per repo running build, lint, tests.

---

## 12. Delivery & ProjectOS

- `.engineering/` installed in both repos with FluxPay values in `config/project.yaml`, `config/architecture.yaml` (module boundaries from §3.1), `config/stack.yaml`; ADRs in `.engineering/memory/adr/`; glossary in `memory/domain/ubiquitous-language.yaml`; shortcuts in `memory/tech-debt/registry.yaml`; `memory/features.yaml` updated as features ship.
- Git: Conventional Commits; short-lived `feat/…` branches merged to `main` via PR; tag `v1.0.0` at release.
- Deployment unchanged: backend on Render (Docker, non-root), frontend on Vercel, Neon Postgres (a fresh database/branch for v1; legacy data untouched). `.env.example` in each repo lists every variable.
- Build order (each a PR): foundation (project skeleton, common, errors, Flyway baseline, health) → identity + merchants → API keys → catalog → payments gateway + checkout → sales + ledger → events/webhooks → analytics + admin → frontend auth + dashboard shell → products/links/developers UI → hosted checkout → analytics UI → E2E + release.
