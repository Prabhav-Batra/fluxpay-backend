# Lifecycles

## Purpose
Define the standard state machines for Tasks, Features, and Releases in ProjectOS.

## 1. Task Lifecycle (Micro)
Applies to individual tickets or AI intents.
1. `DRAFT`: Intent is parsed, Context is gathering.
2. `PLANNED`: Engineering Manifest generated, Policy Engine approved.
3. `IN_PROGRESS`: Code is being written, tests are being run locally.
4. `REVIEW`: PR submitted, Review Engine evaluating.
5. `REJECTED`: Policy or Review engine blocked the change. Back to `IN_PROGRESS`.
6. `DONE`: Merged to `main` branch, Definition of Done met.

## 2. Feature Lifecycle (Macro)
Applies to large epics spanning multiple tasks.
1. `PROPOSAL`: RFC or ADR is drafted.
2. `ACCEPTED`: ADR merged into `memory/adr/`.
3. `DEVELOPMENT`: Multiple tasks execute. Feature flagged off in production.
4. `STAGING_UAT`: Feature complete, deployed to staging for User Acceptance Testing.
5. `PRODUCTION_DARK`: Deployed to production, accessible only to internal testers via feature flag.
6. `GA` (General Availability): Feature flag rolled out to 100% of users.
7. `DEPRECATED`: Replaced by a new feature, scheduled for removal.
8. `REMOVED`: Code deleted from the repository.

## 3. Release Lifecycle (Distribution)
Applies to versioned artifacts.
1. `ALPHA`: Nightly builds, highly unstable.
2. `BETA`: Feature complete, undergoing stabilization.
3. `RC` (Release Candidate): Believed to be stable, undergoing final staging checks.
4. `STABLE`: Tagged, built, deployed to production.
5. `LTS` (Long Term Support): End of active feature development, receives only security patches.
6. `EOL` (End of Life): Unmaintained, deprecated.

## Enforcement
- Workflow Engine gates transitions between these states.
- The `environments.yaml` config maps these lifecycles to physical infrastructure.
