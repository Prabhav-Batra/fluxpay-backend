# Task Classification Engine

## Purpose
Maps a `ParsedIntent` into a standard ProjectOS Workflow and breaks it down into executable sub-tasks.

## Inputs
- `ParsedIntent` (JSON): From the Intent Engine.
- `AvailableWorkflows` (List): Scanned from `.engineering/workflows/*.md`.

## Outputs
- `TaskPlan` (JSON):
  ```json
  {
    "workflow_id": "workflows/new-feature.md",
    "sub_tasks": [
      { "id": 1, "description": "Define OAuth routing in API Gateway", "status": "pending" },
      { "id": 2, "description": "Implement Google OAuth callback in Auth Service", "status": "pending" },
      { "id": 3, "description": "Create 'Login with Google' React component", "status": "pending" }
    ],
    "estimated_complexity": "Medium"
  }
  ```

## Algorithm Steps
1. **Workflow Matching**: Compare `ParsedIntent.primary_action` against the triggers defined in the available workflows.
    - `add_feature` -> `new-feature.md`
    - `fix_bug` -> `bug-fix.md`
    - `refactor` -> `refactor.md`
2. **Task Decomposition**: Break the intent down into atomic steps across the required architectural layers (e.g., Database -> Backend -> API -> Frontend).
3. **Dependency Ordering**: Order the sub-tasks so that dependencies are built first (e.g., API must exist before Frontend can call it).
4. **Complexity Estimation**: Assign Low/Medium/High complexity based on the number of modules affected.

## Edge Cases & Error Handling
- **No Matching Workflow**: User asks for something entirely novel. Fallback: Use `workflows/custom-task.md`.
- **Massive Scope**: Intent translates to > 15 sub-tasks. Fallback: Prompt user to break the task into smaller Epics.
