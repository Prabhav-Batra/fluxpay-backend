# Review Checklist: Architecture

> **Reviewer Instructions**: Validate structural changes against `config/architecture.yaml` and `standards/architecture/*.md`.

## 🔴 Blockers (Must Fix)
- [ ] **Boundary Violations**: Does this PR cross defined bounded contexts improperly? (e.g., `billing` directly accessing `auth`'s database).
- [ ] **Layer Violations**: Does business logic (Domain/Application) import from the presentation or infrastructure layers?
- [ ] **God Files**: Does this introduce or significantly worsen a "God File" (>800 lines) or "God Class"?
- [ ] **Circular Dependencies**: Does this introduce a circular dependency between packages or modules?
- [ ] **State Leakage**: Does a component leak internal state, violating encapsulation?
- [ ] **ADR Missing**: Was a significant architectural change made without an approved Architecture Decision Record (ADR)?

## 🟡 Warnings (Should Fix)
- [ ] **Coupling**: Is the new logic tightly coupled to a specific framework (e.g., Spring/React) rather than being isolated?
- [ ] **Single Responsibility**: Does a new service or class take on too many disconnected responsibilities?
- [ ] **Synchronous Cascades**: Do synchronous API calls form a long chain (A -> B -> C -> D), increasing latency and lowering availability?

## 🟢 Advisories (Nice to Have)
- [ ] **Event-Driven Opportunity**: Could a synchronous integration be replaced with asynchronous events?
- [ ] **Interface Segregation**: Are interfaces small and focused, or do they force implementers to define unused methods?
