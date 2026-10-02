# Separation of Concerns

## Purpose
Ensure that every component in the system has a single, well-defined reason to change. This is the macro-application of the Single Responsibility Principle (SRP).

## Core Rules

1. **UI vs Business Logic**:
   - Visual components (React, Flutter, HTML) must contain zero business rules. They receive data, render it, and emit user actions.
   - If a UI component calculates tax, it violates SoC.

2. **Routing vs Processing**:
   - An API Controller (Express, Spring MVC, FastAPI) has exactly three jobs:
     1. Receive the HTTP request.
     2. Pass the data to the Service layer.
     3. Return the HTTP response.
   - The Controller must never contain `if` statements dictating business flow.

3. **Data Access vs Logic**:
   - The Service layer orchestrates logic but does not know *how* data is stored.
   - The Repository layer knows how data is stored (SQL, Mongo) but does not know *why* it is stored.
   - Never write business logic inside an SQL query (e.g., complex stored procedures that determine user eligibility).

4. **Configuration vs Code**:
   - Externalize all configuration (URLs, secrets, feature flags, retry limits) from the code.
   - The code should remain exactly the same across Staging and Production; only the configuration should change.

## Enforcement
- Enforced strictly by Review Checklists (`code.md` and `architecture.md`).
- A core prompt directive for all AI personas.
