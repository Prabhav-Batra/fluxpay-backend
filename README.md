# fluxpay-backend

FluxPay API — multi-tenant checkout, sales ledger, merchant webhooks and analytics for one-time digital products.

> **v1 rewrite in progress.** The previous implementation is preserved on the `legacy` branch and the `legacy-v0` tag.

- Design spec: [`docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md`](docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md)
- Engineering rules (ProjectOS): [`.engineering/`](.engineering/) — start with `.agents/AGENTS.md`
- Architecture decisions: [`.engineering/memory/adr/`](.engineering/memory/adr/)

Stack: Java 21, Spring Boot 3.5, PostgreSQL 17, Flyway, Gradle (Kotlin DSL).

## Local development

1. Start Docker Desktop (integration tests use Testcontainers).
2. `cp .env.example .env` and fill in a Postgres connection (or run `docker run -p 5432:5432 -e POSTGRES_USER=fluxpay -e POSTGRES_PASSWORD=change-me -e POSTGRES_DB=fluxpay postgres:17-alpine`).
3. `set -a; source .env; set +a; ./gradlew bootRun`
4. `./gradlew spotlessApply build` before every commit.

Health: `GET /health/live`, `GET /health/ready`.

## Razorpay setup

1. Put test-mode keys in `.env` (`RAZORPAY_TEST_KEY_ID`, `RAZORPAY_TEST_KEY_SECRET`).
2. Razorpay Dashboard → Test mode → Webhooks → add `https://<backend>/api/v1/gateway-webhooks/razorpay/test` with events `payment.captured` and `refund.processed`; copy its secret to `RAZORPAY_TEST_WEBHOOK_SECRET`.
3. Enable automatic capture for payments (Account & Settings → Payment capture) so payments reach `captured`.
4. For live mode repeat with `RAZORPAY_LIVE_*` and the `/razorpay/live` URL.

## Receiving FluxPay webhooks (for merchants)

Register an endpoint in the dashboard (Developers → Webhooks). Each POST carries:

- `FluxPay-Event-Id`, `FluxPay-Event-Type` headers and a JSON body `{id, type, created, mode, data}`.
- `FluxPay-Signature: t=<unix seconds>,v1=<hex>` where `v1 = HMAC-SHA256(endpoint_secret, t + "." + raw_body)`.

Verify by recomputing `v1` over the raw body, comparing in constant time, and rejecting `t` older than 5 minutes. Respond 2xx quickly; non-2xx is retried after 1m, 5m, 30m, 2h, 6h, 12h, 24h. Delivery is at-least-once: de-duplicate on `id`. Event types: `checkout.completed`, `checkout.expired`, `sale.refunded`, `webhook.test`.
