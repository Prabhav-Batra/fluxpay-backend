# Custom Task Workflow

**Triggered by:** Novel requests that do not fit into standard workflows (e.g., bug fixes, new features).

## 1. Context Resolution
1. AI analyzes the `ParsedIntent` to determine affected domains.
2. AI selects relevant `standards/*.md` dynamically.

## 2. Planning
1. AI generates an ad-hoc `ExecutionPlan`.
2. Plan must state explicit boundaries to prevent scope creep.

## 3. Execution
1. Implement the task.
2. Adhere to global constraints in `config/constraints.yaml`.

## 4. Verification
1. Run existing test suite to ensure no regressions.
2. Ensure new files conform to `ARCHITECTURE.md`.
