# Definition of Done (DoD)

## Purpose
The absolute checklist that must be satisfied before any piece of work (Feature, Bug Fix, Refactor) is considered "Done" and ready for production.

## Global Criteria (Applies to all PRs)

### 🔴 Code & Quality (Blockers)
- [ ] Code compiles and builds without any errors or new warnings.
- [ ] Code passes all automated linters and static analysis (e.g., SonarQube, ESLint).
- [ ] Code has been peer-reviewed (or AI-reviewed via the Review Engine) and all blockers are resolved.
- [ ] Code adheres to all applicable `.engineering/standards/`.

### 🔴 Testing (Blockers)
- [ ] Unit tests are written for all new business logic.
- [ ] Test coverage for the modified files has not decreased.
- [ ] All automated tests (Unit, Integration, E2E) pass in the CI pipeline.

### 🔴 Documentation & Context (Blockers)
- [ ] If external APIs were modified, the OpenAPI/Swagger spec is updated.
- [ ] If environment variables were added/removed, `.env.example` and infrastructure config are updated.
- [ ] The `CHANGELOG.md` or release notes draft has been updated if this is a user-facing change.

### 🔴 Security & Deployment (Blockers)
- [ ] No hardcoded secrets or PII logging introduced.
- [ ] CI/CD vulnerability scanners report 0 new High/Critical vulnerabilities.
- [ ] The code has been successfully deployed to a staging environment and verified.

## Workflow-Specific Variations
- **Database Migrations**: Require an explicit `DOWN` script and verification on a clone of production data.
- **Security Patches**: Require immediate fast-track deployment and post-deployment audit logs verification.
- **Incident Hotfixes**: May temporarily bypass the "Unit Test" requirement for immediate mitigation, but a tech-debt ticket MUST be created to backfill tests within 48 hours.

## Enforcement
- The Output Engine will not finalize a task unless the AI explicitly verifies the DoD.
- GitHub PR templates should include this checklist.
