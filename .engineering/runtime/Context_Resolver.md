# Context Resolver (Context Loader)

## 1. Responsibility
The first subsystem to run. It calculates the AI's token budget and dynamically loads only the most relevant context into the AI's memory window. It acts as the gatekeeper against hallucination (caused by "flying blind") and forgetting (caused by "context overflow").

## 2. Process & Heuristics
1. **Budget Calculation**: Determines available tokens (e.g., 70% of 128k context window).
2. **Priority 1 (Core Constitution)**: Always loads `config/constraints.yaml` and `standards/architecture/Engineering_Principles.md`.
3. **Priority 2 (Task-Specific)**: Reads the `TaskPlan`. If it touches APIs, loads `standards/api.md`. If it uses React, loads `plugins/React.md`.
4. **Priority 3 (Source Code)**: Uses the Repository Scanner and Impact Analyzer to find the exact source files implicated by the task.
5. **Context Pruning**: If tokens exceed the budget, it aggressively prunes (e.g., loading only class signatures instead of full implementations).

## 3. The Universal Contract
**Inputs**: `TaskPlan`, `RawTopologyData`
**Outputs**: `ContextPayload` (Optimized array of file contents)
**Dependencies**: `Repository_Scanner`, `Impact_Analyzer`
**Failure Modes**: Halts execution if context still overflows after pruning.

## 4. Output Schema
```yaml
engine: "ContextResolver"
status: "Success"
confidence: 95
artifacts:
  - context_payload:
      - ".engineering/standards/architecture/Engineering_Principles.md"
      - ".engineering/config/constraints.yaml"
      - ".engineering/plugins/Python.md"
      - "src/api/routes.py"
next: "Policy Engine"
```
