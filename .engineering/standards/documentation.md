# Global Standard: Documentation

## Code-Level Documentation
1. **Document the "Why", not the "What"**: Code explains *what* and *how*. Comments should explain *why* a particular approach was taken, especially if it's non-obvious, a workaround, or a business rule.
2. **Public APIs**: Every public class, method, and API endpoint must have a documentation block (e.g., JSDoc, JavaDoc, Docstrings) explaining its purpose, parameters, return values, and exceptions thrown.
3. **Keep comments updated**: A misleading comment is worse than no comment. When modifying code, you must update the associated comments.
4. **Avoid redundant comments**: Do not write comments that just restate the code. Example of a bad comment: `// Increment i by 1 \n i++`.

## Repository Documentation
5. **README.md is mandatory**: Every repository or distinct package must have a README explaining:
    - What the project does
    - Prerequisites and dependencies
    - How to build and run locally
    - How to run tests
    - Deployment instructions
6. **Architecture Decision Records (ADRs)**: Any significant architectural decision must be documented as an ADR in the `memory/adr/` directory. ADRs capture the context, options considered, and the final decision.
7. **Changelog**: Maintain a `CHANGELOG.md` file following the "Keep a Changelog" format to document notable changes for each version.

## API Documentation
8. **OpenAPI Specification**: REST APIs must be documented using an OpenAPI (Swagger) spec. The spec must be kept in sync with the implementation, ideally generated from code or annotations.
9. **API Examples**: Documentation must include examples of requests and responses for success and error scenarios.

## Developer Experience
10. **Onboarding guide**: Complex projects should have an `ONBOARDING.md` or a wiki page to help new team members (and AI agents) get up to speed quickly.
11. **Runbooks**: Operational services must have runbooks detailing how to handle common incidents, alerts, and operational tasks.
12. **Self-documenting code**: Strive to write code so clear and well-named that it requires minimal inline documentation.
