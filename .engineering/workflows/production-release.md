# Workflow: Production Release

## 1. Required Inputs
- Release candidate version number.
- Changelog or list of merged features/fixes.

## 2. Required Context
- Deployment pipelines (`environments.yaml`).
- Rollback procedures.

## 3. Required Reviews
- Production Readiness Review (`reviews/production-readiness.md`)

## 4. Expected Outputs
- A tagged release in version control.
- Artifacts built and pushed to the container registry.

## 5. Exit Criteria
- All staging checks pass.
- Application successfully deploys to production without triggering alert monitors.

## 6. Execution Steps
1. **Code Freeze**: Lock the main/release branch to prevent late-breaking commits.
2. **Staging Verification**: Ensure all end-to-end (E2E) and integration tests have passed on the staging environment against the final release candidate.
3. **Changelog Generation**: Auto-generate `CHANGELOG.md` based on conventional commits. Tag the repository (e.g., `git tag v1.2.0`).
4. **Artifact Build**: The CI server builds immutable artifacts (e.g., Docker images, mobile binaries) and pushes them to the secure registry.
5. **Database Migration**: Apply any pending `UP` database migrations to the production database *before* traffic shifts.
6. **Canary / Blue-Green Rollout**: Shift 10% of traffic to the new version. Monitor error rates for 5 minutes. If stable, increase to 100%.
7. **Post-Release Sanity Check**: Run a lightweight smoke test against the live production environment. Announce release success to stakeholders.
