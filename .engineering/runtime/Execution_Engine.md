# Execution Engine

## Purpose
The core loop. Executes the `TaskPlan` step-by-step, generating code, modifying files, and running terminal commands while constrained by the Context and Technology Resolvers.

## Inputs
- `TaskPlan` (JSON): The steps to execute.
- `ContextPayload` (Files): The rules to follow.
- `ActiveTechPacks` (Packs): The framework rules.

## Outputs
- `ExecutionTrace` (Log): A step-by-step record of files changed and commands run.
- Modified source code on disk.

## Algorithm Steps
1. **Manifest Generation**: Before writing any code, generate an `Engineering Manifest` (using the template) and present it to the Architecture Guard (Policy Engine).
2. **Task Iteration**: For each sub-task in the `TaskPlan`:
   a. **Read**: View existing target files.
   b. **Plan**: Formulate the exact file edits.
   c. **Write**: Apply edits (create/update files).
   d. **Verify**: Run local linters or compiler checks (e.g., `npm run lint`, `go build`).
3. **Error Recovery Loop**: If a verify step fails, read the error output, modify the code, and retry. Cap at 3 retries per sub-task to prevent infinite loops.
4. **Task Completion**: Mark the sub-task as `status: complete` and move to the next.

## Edge Cases & Error Handling
- **God File Violation**: A file edit pushes a file over the threshold defined in `File_Size_Rules.md`. Fallback: The Execution Engine blocks the write, splits the logic into a new file, and updates imports.
- **Infinite Loop**: The compiler error recovery loop hits 3 retries without success. Fallback: Pause execution, present the error to the human user, and ask for guidance.
