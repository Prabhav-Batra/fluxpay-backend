# Dependency Rules

## Purpose
Prevent spaghetti architecture, cyclic dependencies, and tight coupling to volatile external libraries.

## Core Rules

1. **Direction of Dependencies (Clean Architecture)**:
   - Dependencies must always point *inward* toward the Domain layer.
   - The Domain layer must have **zero** outbound dependencies (no database drivers, no UI frameworks, no external HTTP clients).
   - The Application layer depends only on the Domain.
   - Infrastructure and Presentation layers depend on Application and Domain.

2. **Acyclic Dependencies Principle (ADP)**:
   - The dependency graph of packages/modules must be a Directed Acyclic Graph (DAG).
   - If Package A depends on Package B, and B depends on C, C **must not** depend on A.
   - **Resolution**: Break cycles by extracting the shared interface into a new Package D, or use Dependency Inversion.

3. **External Vendoring & Wrapping**:
   - Never leak volatile third-party SDKs (e.g., AWS SDK, Stripe API) directly into business logic.
   - Wrap external libraries in an interface defined by your Application layer, and implement the interface in the Infrastructure layer (Adapter Pattern).

4. **Stable Dependencies Principle (SDP)**:
   - Depend in the direction of stability. A module should only depend on modules that are *more stable* (less likely to change) than itself.
   - (e.g., A volatile UI component depends on a highly stable core entity, not the other way around).

## Enforcement
- The `Dependency_Graph` intelligence module runs on every PR.
- The `Policy_Engine` will hard-block any PR that introduces a circular dependency or violates the inward-pointing rule.
