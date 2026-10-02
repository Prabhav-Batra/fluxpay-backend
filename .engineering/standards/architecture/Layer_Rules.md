# Layer Rules (Clean Architecture)

## Purpose
Establish strict vertical separation within a codebase to ensure business logic is isolated from delivery mechanisms and databases.

## The 4 Canonical Layers

### 1. Domain Layer (Core)
- **Responsibility**: Enterprise-wide business rules, entities, and value objects.
- **Allowed Imports**: NONE. Must be completely independent.
- **Content**: Plain classes, interfaces, enums. No framework annotations (no `@Entity`, no `@Component`).

### 2. Application Layer (Use Cases)
- **Responsibility**: Application-specific business rules. Orchestrates the flow of data to and from the domain entities.
- **Allowed Imports**: Domain Layer.
- **Content**: Services, Use Cases, CQRS Handlers, Port Interfaces (e.g., `UserRepository` interface).

### 3. Presentation Layer (Delivery)
- **Responsibility**: Handles input from the outside world (HTTP, CLI, GUI) and translates it.
- **Allowed Imports**: Application Layer, Domain Layer.
- **Content**: REST Controllers, GraphQL Resolvers, CLI Commands, UI Views.

### 4. Infrastructure Layer (Adapters)
- **Responsibility**: Connects the application to external systems (DB, Message Bus, 3rd Party APIs).
- **Allowed Imports**: Application Layer, Domain Layer.
- **Content**: SQL Repositories, Kafka Producers, Stripe Clients. Implements the Port Interfaces defined in the Application layer.

## The Golden Rule of Layers
**The Domain and Application layers must NEVER know about the Presentation or Infrastructure layers.**

## Enforcement
- Caught during Architecture Review.
- Checked dynamically by the `Dependency_Graph` intelligence module.
