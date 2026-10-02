# Workflow Engine

## Purpose
Manages Standard Operating Procedures (SOPs). It ensures that complex engineering tasks are executed in a deterministic, multi-step sequence with proper gates and reviews.

## Inputs
- `ParsedIntent` from the Intent Engine.
- Workflow definitions (`workflows/*.md`).

## Outputs
- `WorkflowInstance`: A stateful object tracking the progress of the execution.

## Algorithm Steps
1. **Workflow Matching**: Find the most specific workflow for the intent. If an intent is "Fix DB migration", choose `database-change.md` over the generic `bug-fix.md`.
2. **Gate Evaluation**: Before starting execution, verify all `Required Inputs` are present.
   - If the workflow requires an ADR and none exists in `memory/adr/`, halt and instruct the AI/human to write the ADR first.
3. **Task Composition**: If a task is extremely complex (e.g., "Build a new React app with a Go backend"), the engine composes multiple workflows sequentially (`new-service.md` -> `new-client.md`).
4. **Execution Tracking**: Monitor the `Execution_Engine` as it works through the `Execution Steps` defined in the workflow.
5. **Exit Gate Check**: Verify all `Exit Criteria` before allowing the Output Engine to finalize the task.

## Custom Workflow Registration
Teams can define custom workflows in `workflows/custom/`. The engine automatically parses them and adds them to the matching pool based on the `1. Required Inputs` headers.
