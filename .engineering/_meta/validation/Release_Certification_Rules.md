# Release Certification Rules

A Release cannot be certified until all subsystems pass Validation.

- Rule 1: No failed tests in `make validate`.
- Rule 2: 100% Architecture compliance (No God Files).
- Rule 3: All Quality Gates in `.engineering/ENGINEERING/RELEASES/` are explicitly checked.
