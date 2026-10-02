# fluxpay-backend

FluxPay API — multi-tenant checkout, sales ledger, merchant webhooks and analytics for one-time digital products.

> **v1 rewrite in progress.** The previous implementation is preserved on the `legacy` branch and the `legacy-v0` tag.

- Design spec: [`docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md`](docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md)
- Engineering rules (ProjectOS): [`.engineering/`](.engineering/) — start with `.agents/AGENTS.md`
- Architecture decisions: [`.engineering/memory/adr/`](.engineering/memory/adr/)

Stack: Java 21, Spring Boot 3.5, PostgreSQL 17, Flyway, Gradle (Kotlin DSL).
