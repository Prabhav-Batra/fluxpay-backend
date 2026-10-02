# Standards Engine

## Purpose
Resolves which engineering standards apply to the current task. It manages the hierarchy of standards to ensure that specific project overrides win over generic global advice.

## Inputs
- `TaskPlan`.
- Global Standards (`standards/*.md`).
- Technology Standards (`registry/packs/`).
- Local Overrides (`.projectos/constraints.yaml` within a specific microservice).

## Outputs
- `ActiveRuleSet`: A flattened list of all applicable engineering rules.

## Algorithm Steps
1. **Global Resolution**: Based on the `TaskPlan`, select the relevant base standards (e.g., `api.md`, `database.md`).
2. **Technology Resolution**: Query the `Technology_Resolver` for framework-specific rules (e.g., `spring-boot/best-practices.md`).
3. **Local Override Resolution**: Check if the target directory contains a nested `.projectos/` configuration. If a local rule conflicts with a global rule, the local rule wins.
4. **Conflict Merging**: Flatten the rules into a single context block.
   - *Example Conflict*: Global `api.md` says "Use REST", but `src/grpc-service/.projectos/constraints.yaml` says "Use gRPC". The engine produces an `ActiveRuleSet` prioritizing gRPC for this specific execution.

## Applicability Matrix
The engine tags each rule in the `ActiveRuleSet` with its source origin (Global, Tech Pack, Local) to provide explainability if the AI's decisions are questioned.
