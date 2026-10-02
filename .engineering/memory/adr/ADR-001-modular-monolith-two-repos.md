# ADR-001: Modular monolith in two repos

- **Status:** Accepted
- **Date:** 2026-10-02
- **Spec:** docs/superpowers/specs/2026-10-02-fluxpay-v1-design.md

## Context
The previous FluxPay had four repos (backend, dashboard, second checkout app, webhook worker) with duplicated checkout code and a worker that the backend had already replaced.

## Decision
One Spring Boot application (`fluxpay-backend`) organised as package-by-feature modules, and one Next.js application (`fluxpay-frontend`) serving dashboard, admin and hosted checkout. `fluxpay-webhook-worker` and `fluxpay-customer-frontend` are archived.

## Consequences
Fewer deploys and no duplicated code. Module boundaries must be enforced in code (ArchUnit) because there is no network boundary. A module can be extracted later if load requires it.
