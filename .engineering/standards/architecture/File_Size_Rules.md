# File Size Rules

## Purpose
A core defense against the "God File" anti-pattern. Enforces maintainability by putting hard caps on file complexity.

## The Caps

| File Type / Layer | Soft Warning (🟡) | Hard Limit (🔴) |
|-------------------|-------------------|-----------------|
| Controllers / Routes | 150 lines | 300 lines |
| Domain Entities | 200 lines | 400 lines |
| Services / Use Cases | 250 lines | 500 lines |
| UI Components | 150 lines | 300 lines |
| Configuration / YAML | 300 lines | 800 lines |
| Tests | 400 lines | 1000 lines |

*(Note: Lines of Code (LOC) ignores comments and whitespace).*

## Resolution Strategies (When hitting limits)

1. **The Controller is too big**:
   - Are you writing business logic in the controller? Extract it to a Service.
   - Are you manually mapping JSON to objects? Extract it to a DTO/Mapper.
2. **The Component is too big**:
   - Extract independent visual sub-trees into their own child components.
   - Extract complex `useEffect`/state logic into a custom hook.
3. **The Service is too big**:
   - The service is likely violating the Single Responsibility Principle. Split `UserService` into `UserRegistrationService` and `UserBillingService`.

## Enforcement
- The `Policy_Engine` reads these thresholds.
- If an AI attempts to write a file exceeding the Hard Limit, the Engine intercepts the output, returns an error, and forces the AI to break the logic into smaller files before writing to disk.
- If a human pushes a file exceeding the limit, CI will fail the PR unless overridden by a Lead Architect.
