# Merge Process

## Purpose
Ensure that code integrating into the `main` or `release` branch is safe, tested, and compliant.

## Pre-Merge Requirements (The Pipeline)
Before a Pull Request can be merged, the following automated checks MUST pass:
1. **Linting & Formatting**: Code strictly adheres to stylistic rules (e.g., Prettier, Black, Gofmt).
2. **Static Analysis (SAST)**: SonarQube/CodeQL reports 0 critical issues and no drop in overall maintainability rating.
3. **Unit & Integration Tests**: 100% pass rate.
4. **Coverage Gate**: Code coverage must not drop. New files must have >80% coverage.
5. **ProjectOS Validation**: The `Review_Engine` signs off with no 🔴 Blockers.

## Human Approvals
- **Standard PRs**: Require at least 1 approval from a peer engineer.
- **Architectural PRs**: (Touching `config/`, `standards/`, or core domain models) Require approval from a Lead Architect/Code Owner.
- **Emergency Hotfixes**: May be merged by an Incident Commander with 0 approvals if CI is bypassed, but requires a post-merge review.

## Merge Strategy
1. **Squash and Merge**: Feature branches must be squashed into a single, atomic commit when merging into `main`. This keeps the history linear and clean.
2. **Conventional Commits**: The squashed commit message MUST follow the conventional commit format:
   - `feat: [scope] Add user registration endpoint`
   - `fix: [scope] Resolve memory leak in image processing`
   - `docs: Update API specification`
3. **Branch Deletion**: The source branch is automatically deleted upon merge.

## Post-Merge
- Merging to `main` automatically triggers the Staging CD pipeline.
- Production deployment is triggered manually via the `production-release` workflow or by tagging a release commit.
