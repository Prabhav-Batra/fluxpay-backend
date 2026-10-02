# Engineering Planning Engine (EPE)

## 1. Responsibility
The Engineering Planning Engine prevents the "rush to code" anti-pattern. It sits between the Context Loader and the Execution Engine. Before a single line of code is written, it synthesizes the loaded context and the parsed intent to generate a step-by-step Execution Plan.

## 2. Process & Heuristics
- **Dependency Mapping**: Traces all affected modules (e.g., Auth -> Users -> Database).
- **Risk Assessment**: Identifies potential breaking changes or security risks.
- **Step Generation**: Breaks the task into discrete, actionable implementation steps.
- **Manifest Generation**: Generates the mandatory `Engineering Manifest` detailing the business goal, bounded context, required reviews, definition of done, and every exact module and file that will be created.
- **Testing Strategy**: Determines exactly what tests must be written to prove the feature works.

## 3. The Universal Contract
**Inputs**: `ParsedIntent`, `ContextPackage`
**Outputs**: `ExecutionPlan`
**Dependencies**: `Dependency Graph`
**Failure Modes**: Halts execution if a required standard is missing from the Context Package.

## 4. Output Schema
```yaml
engine: "Planning"
status: "Success"
confidence: 98
artifacts:
  - engineering_manifest:
      feature: "Wallet Service"
      business_goal: "Allow users to manage balances securely."
      bounded_context: "Wallet"
      modules: ["wallet-api", "wallet-domain"]
      files: ["WalletController.java", "WalletService.java"]
      tests: ["WalletServiceTest.java"]
      reviews: ["Architecture", "Security"]
      definition_of_done: ["Tests pass", "Documentation updated"]
  - plan:
      step_1: "Implement WalletDomain"
  - risks: ["Downtime required for migration"]
next: "Policy Engine (Architecture Guard)"
```
