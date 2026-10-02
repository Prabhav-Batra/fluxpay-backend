# Package Rules

## Purpose
Define how code is physically grouped into directories/packages to maximize cohesion and minimize coupling.

## Core Rules

1. **Package by Feature, Not by Layer**:
   - **❌ BAD (By Layer)**: `src/controllers`, `src/services`, `src/repositories`. This scatters a single feature across the entire codebase.
   - **✅ GOOD (By Feature/Component)**: `src/auth`, `src/billing`, `src/inventory`. Everything related to Auth lives in the Auth package.

2. **The Common/Shared Package Anti-Pattern**:
   - Do not create a massive `src/common` or `src/shared` package. It becomes a dumping ground for unrelated code.
   - If a utility is only used by `billing`, keep it in `src/billing/utils`.
   - If a utility is truly universal (e.g., a DateTime formatter), put it in a highly specific package like `src/runtime/time`.

3. **High Cohesion**:
   - Files that change together should live together. If you frequently have to modify 5 different directories to ship a single feature, your package boundaries are wrong.

4. **No Cyclic Dependencies Between Packages**:
   - Covered by `Dependency_Rules.md`. If Package A needs B, and B needs A, they either belong in the same package, or the shared logic belongs in a third package.

## Enforcement
- Caught during Code Review and Architecture Review.
- Refactoring workflows often target resolving `src/shared` dumping grounds.
