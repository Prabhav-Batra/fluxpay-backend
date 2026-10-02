# Workflow: New Feature

## 1. Required Inputs
- Feature Requirement Document (PRD) or User Story.
- UX/UI Designs (if applicable).

## 2. Required Context
- `architecture.yaml`
- `standards/backend.md` (or frontend/mobile depending on scope).
- `standards/api.md`.

## 3. Required Reviews
- Code Review (General)
- Security Review (if handling user data).
- UI/UX Review (if applicable).

## 4. Expected Outputs
- New source files implementing the feature.
- Unit tests covering the new logic.
- Updated API documentation.

## 5. Exit Criteria
- Tests pass.
- Linter passes.
- PR is approved and merged.

## 6. Execution Steps
1. **Manifest Generation**: Read the PRD/Story and generate the `Engineering Manifest`.
2. **Architecture Validation**: Validate the manifest against `constraints.yaml` and `Architecture_Guard`.
3. **Branching**: Create a new feature branch `feat/issue-description`.
4. **Scaffolding**: If new domain entities are needed, create them first in the Domain Layer.
5. **Data Layer**: Implement repositories and data access models.
6. **Business Logic**: Implement use cases/services.
7. **Presentation**: Implement API controllers or UI components.
8. **Testing**: Write unit and integration tests for the new feature.
9. **Verification**: Run `Verification_Engine` checks locally.
10. **Commit**: Format changes via `Output_Engine` and push to VCS.
