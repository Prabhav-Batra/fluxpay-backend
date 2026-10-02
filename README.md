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
