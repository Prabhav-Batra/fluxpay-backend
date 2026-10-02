# Explainability Engine

## Purpose
Translates the raw JSON logs from the Decision Trace into human-readable explanations. It answers the question: "Why did the AI do this?" in plain English.

## Inputs
- `DecisionTraceData` (JSONL): The raw flight data recorder logs.

## Outputs
- `HumanReadableExplanation` (Text/Markdown).

## Algorithm Steps
1. **Trace Parsing**: Read the JSONL trace for a specific task ID.
2. **Event Filtering**: Filter out low-level noise (e.g., `File_Read_Success`) and keep high-level cognitive decisions (e.g., `Workflow_Selected`, `Standard_Injected`, `Verification_Failed`).
3. **Template Translation**: Map specific trace actions to human-readable templates.
   - *Example Mapping*: `{"engine": "Task_Classification", "action": "Select", "target": "bug-fix"}` -> "I classified this as a bug fix..."
4. **Rationale Appending**: Append the `rationale` string from the trace to the translated sentence. -> "...because the user provided a stack trace and requested a resolution."
5. **Formatting**: Structure the output chronologically into phases (Planning, Context, Execution, Verification).

## Example Output
```markdown
### Execution Summary
1. **Planning**: I classified this as a `new-feature` because the intent was to add Google OAuth.
2. **Context**: I loaded `standards/security.md` because this task touches the authentication domain.
3. **Execution**: I modified `src/auth/routes.ts`. I was blocked once by the `Architecture Guard` because the file exceeded 300 lines, so I split the logic into `src/auth/oauth.ts`.
4. **Verification**: Tests passed on the second attempt after fixing a missing import.
```

## Integration
- This engine powers the final summary provided by the **Output Engine** to the human user in the terminal or PR description.
