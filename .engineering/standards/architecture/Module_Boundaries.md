# Module Boundaries

## Purpose
Prevent a monolithic codebase from degrading into a "Big Ball of Mud" by enforcing strict horizontal boundaries between functional domains (Bounded Contexts).

## Core Rules

1. **Explicit Exports Only**:
   - A module (e.g., `src/billing`) must have a well-defined public API. 
   - Other modules may only import from this public API (e.g., `src/billing/index.ts` or `src/billing/public/`).
   - Importing deeply nested files from another module (e.g., `import { calculate } from '../billing/internal/calc'`) is strictly forbidden.

2. **No Shared Database Integration**:
   - Module A should never directly query the database tables owned by Module B.
   - If Module A needs data from Module B, it must call Module B's public API or listen to Module B's domain events.

3. **Event-Driven Crossing**:
   - For loosely coupled cross-module communication, prefer Domain Events over synchronous API calls.
   - e.g., When `UserAuth` creates a user, it emits `UserCreatedEvent`. `Billing` listens to this event to create a customer profile, rather than `UserAuth` directly calling `BillingService.createCustomer()`.

4. **Independent Testability**:
   - You should be able to run the unit and integration tests for a single module without loading the other modules into memory.

## Enforcement
- Caught during Architecture Review.
- Checked dynamically by the `Dependency_Graph` intelligence module by grouping files into domain folders and looking for cross-domain violations.
