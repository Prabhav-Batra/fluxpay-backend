# Review Checklist: Documentation

> **Reviewer Instructions**: Validate that the code is self-explanatory and external documentation is kept in sync.

## 🔴 Blockers (Must Fix)
- [ ] **README Updates**: If this PR introduces a new service, script, or environment variable, is the README updated?
- [ ] **API Spec Sync**: Does the OpenAPI/Swagger spec exactly match the implemented code?
- [ ] **Missing ADR**: Did this PR introduce a new pattern or tool without an accompanying Architecture Decision Record?

## 🟡 Warnings (Should Fix)
- [ ] **Obsolete Comments**: Does this PR modify code but leave the old comments intact, making them misleading?
- [ ] **Why vs What**: Do the inline comments explain *why* the code does something rather than just repeating *what* it does?
- [ ] **Changelog**: Should this change be documented in the `CHANGELOG.md` for end-users?
- [ ] **Commit Messages**: Do the commit messages follow the Conventional Commits format and explain the rationale?

## 🟢 Advisories (Nice to Have)
- [ ] **Runbooks**: If this introduces a new failure mode, is the operational runbook updated?
- [ ] **Docstrings**: Are public interfaces and methods documented with standard docstrings (JSDoc, JavaDoc)?
