# ProjectOS Runtime Engines (The Pipeline)

This directory contains the core cognitive algorithms that dictate how an AI assistant processes a task within ProjectOS.

The engines execute in a strict, acyclic 4-stage pipeline.

## Stage 1: Discovery & Intent
1. **[Intent Engine](./Intent_Engine.md)**: Parses human language into semantic intent.
2. **[Task Classification Engine](./Task_Classification_Engine.md)**: Determines task scope and complexity.
3. **[Technology Resolver](./Technology_Resolver.md)**: Identifies required tech stack and plugins.
4. **[Context Resolver](./Context_Resolver.md)**: Loads the exact config, standards, and code needed.

## Stage 2: Policy & Strategy
5. **[Policy Engine](./Policy_Engine.md)**: Evaluates context against hard constraints. Stops dangerous actions.
6. **[Planning Engine](./Planning_Engine.md)**: Synthesizes a step-by-step Execution Plan.

## Stage 3: Action & Validation
7. **[Execution Engine](./Execution_Engine.md)**: Produces code strictly following the Execution Plan.
8. **[Verification Engine](./Verification_Engine.md)**: Ensures the generated code compiles, tests pass, and it matches the plan.

## Stage 4: Assurance & Delivery
9. **[Review Engine](./Review_Engine.md)**: Evaluates the verified code against loaded standards and checklists.
10. **[Output Engine](./Output_Engine.md)**: Formats the final deliverables for the user.

## Supporting Intelligence Modules
These modules are queried by the engines above to gather data about the repository:
- `Repository_Scanner.md`
- `Repository_Index.md`
- `Dependency_Graph.md`
- `Impact_Analyzer.md`
- `Knowledge_Graph.md`
- `Decision_Trace.md`
- `Explainability_Engine.md`
