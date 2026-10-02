# Output Engine

## Purpose
The final stage of the pipeline. Synthesizes the results of the execution and verification into a human-readable format, updates Project Memory, and handles Git operations.

## Inputs
- `VerificationReport` (JSON): From the Verification Engine.
- `ExecutionTrace` (Log): From the Execution Engine.

## Outputs
- Commit to Version Control.
- Terminal output to the human user.
- Updated files in `.engineering/memory/`.

## Algorithm Steps
1. **Memory Update**: 
   - If an architectural decision was made, generate an ADR and save to `memory/adr/`.
   - If technical debt was incurred (e.g., hardcoded a value to save time), log it to `memory/tech-debt/`.
   - Update `memory/domain/ubiquitous-language.yaml` if new domain terms were introduced.
2. **Git Commit**: Formulate a Conventional Commit message summarizing the changes (e.g., `feat(auth): implement Google OAuth callback`).
3. **Pull Request (Optional)**: If configured, generate a PR description containing the "Why", "What", and "How to Test", linking to the original intent/ticket.
4. **Terminal Formatting**: Print a concise summary to the user using markdown, highlighting:
   - What was built.
   - What tests were run.
   - Any technical debt added.

## Edge Cases & Error Handling
- **Verification Failed**: If the input `VerificationReport` indicates failure (and the Execution Engine couldn't recover), the Output Engine must NOT commit to Git. It formats the error report for the human user and asks for manual intervention.
- **Merge Conflicts**: During Git operations, a conflict is detected. Fallback: Abort the commit, print the conflicting files, and instruct the user to resolve them manually.
