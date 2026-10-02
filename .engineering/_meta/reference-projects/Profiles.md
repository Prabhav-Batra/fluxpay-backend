# Reference Projects Architecture

To maximize reuse, Reference Projects are stored as declarative profiles mapping plugins to standards.

- **FinTech Profile**: Injects `PostgreSQL`, `Kafka`, `Spring_Boot`. Enforces `AuditLogging` and `Idempotency`.
- **Social Platform**: Injects `Redis`, `React`, `Python`. Enforces `EventSourcing`.
- **SaaS Platform**: Injects `Auth0` (Custom Plugin), `Spring_Boot`. Enforces `RBAC` and `TenantIsolation`.
