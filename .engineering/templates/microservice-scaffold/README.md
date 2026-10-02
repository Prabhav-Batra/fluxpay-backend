# Microservice Scaffold Template

This directory provides the canonical structure for a new microservice in ProjectOS following DDD and Clean Architecture principles.

```text
{{ SERVICE_NAME }}/
├── .projectos/                # Service-level constraints (overrides global)
│   ├── constraints.yaml
│   └── stack.yaml
├── cmd/                       # Entry points
│   └── server/                # Main application bootstrap
│       └── main.go (or main.ts, Application.java)
├── internal/                  # Private application code (Go convention, optional elsewhere)
│   ├── domain/                # Enterprise business rules
│   │   ├── entities/          # Core domain models
│   │   ├── events/            # Domain events (e.g., UserCreated)
│   │   └── repositories/      # Interfaces for data access
│   ├── application/           # Application business rules
│   │   ├── usecases/          # Commands and Queries (CQRS)
│   │   └── dtos/              # Data Transfer Objects
│   ├── infrastructure/        # Frameworks and drivers
│   │   ├── database/          # Postgres/Mongo implementations of repositories
│   │   ├── messaging/         # Kafka/RabbitMQ publishers/consumers
│   │   └── third_party/       # External API clients
│   └── presentation/          # Interface adapters
│       ├── http/              # REST Controllers / Routers
│       ├── grpc/              # gRPC Handlers
│       └── cli/               # CLI Commands
├── migrations/                # Database migration scripts
├── pkg/                       # Public libraries intended to be imported by other services
├── Dockerfile                 # Multi-stage build definition
├── Makefile                   # Common tasks (build, test, run)
└── README.md                  # Service documentation
```

## How to use this template

1. Copy this structure to the root of the new service.
2. Replace `{{ SERVICE_NAME }}` with the actual bounded context name.
3. Keep the dependency flow inward: `presentation` -> `application` -> `domain`. `infrastructure` also points inward to `domain` (implementing its interfaces).
