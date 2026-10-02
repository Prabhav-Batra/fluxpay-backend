# Global Standard: Git & Version Control

## Branching Strategy
1. **Trunk-Based Development**: Prefer short-lived feature branches branching from `main`. Merge frequently. Long-running feature branches (lasting > 2 days) are heavily discouraged.
2. **Branch naming**: Use the format `type/issue-id-description`. Types: `feat`, `fix`, `chore`, `refactor`, `docs`. Example: `feat/PRJ-123-user-auth`.
3. **Never push to main directly**: The `main` branch must be protected. All changes must go through a Pull Request.

## Commits
4. **Conventional Commits**: All commit messages must follow the Conventional Commits specification (`type(scope): subject`).
5. **Atomic commits**: Each commit should represent a single logical change. If a commit breaks the build, it is too large or encompasses too many concepts.
6. **Detailed commit bodies**: For complex changes, use the commit body to explain *why* the change was made, not just *what* changed.
7. **No WIP commits in history**: Squash or interactive rebase "WIP" or "fix typo" commits before merging a PR. The `main` history must be clean and readable.

## Pull Requests
8. **Small PRs**: Pull requests should ideally be under 400 lines of code changed (excluding generated files or lockfiles). Large PRs are impossible to review effectively.
9. **PR descriptions**: Every PR must have a description detailing the problem being solved, the approach taken, and instructions for how to test it.
10. **Draft PRs**: Open Draft PRs early for feedback on approach before finalizing the implementation.
11. **Review requirements**: Every PR requires at least one approval from a code owner and must pass all CI checks before merging.
12. **Squash and merge**: Use "Squash and Merge" to bring feature branches into `main`. This keeps the main history linear (one commit per PR).

## Releases
13. **Semantic Versioning**: Use SemVer (`MAJOR.MINOR.PATCH`) for tagging releases.
14. **Release tags**: All production releases must be tagged in Git (e.g., `v1.2.0`).

## Repository Maintenance
15. **Clean up branches**: Delete feature branches immediately after they are merged.
16. **Ignore files**: Maintain comprehensive `.gitignore` files. Never commit compiled binaries, local environment variables (`.env`), or OS-specific files (`.DS_Store`).
