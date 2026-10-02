# Review Engine

## Purpose
Automates the Code Review and Architecture Review processes. It dynamically composes checklists based on the scope of the change and enforces severity-based blocking.

## Inputs
- `ExecutionTrace` (what files were changed).
- `ReviewChecklists` (`reviews/*.md`).

## Outputs
- `ReviewReport` (JSON):
  ```json
  {
    "status": "failed",
    "blockers": ["security: Missing authentication check on new endpoint"],
    "warnings": ["code: Cyclomatic complexity is high in processPayment"],
    "passed": ["database", "architecture"]
  }
  ```

## Algorithm Steps
1. **Checklist Composition**: If a PR touches database migrations, React components, and an API route, the engine loads `database.md`, `ui-ux.md`, `api.md`, and `security.md`.
2. **Severity Mapping**: Every item in a checklist is internally mapped to a severity:
   - 🔴 **Blocker**: Security vulnerabilities, boundary violations, missing tests.
   - 🟡 **Warning**: Naming conventions, minor performance regressions.
3. **Automated Evaluation**: Feed the changed files and the composed checklist to the AI as a distinct "Reviewer Persona". The AI acts as an adversary to the "Engineer Persona" that wrote the code.
4. **Decision Logic**:
   - If ANY Blocker fails, the Review Engine returns `status: failed`.
   - If Warnings fail, it returns `status: passed_with_warnings`.
5. **Human Escalation**: If the AI reviewer cannot confidently determine if a check passes (e.g., "Does this match the Figma design?"), it flags the item for manual human review in the GitHub PR.
