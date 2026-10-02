# Verification Engine

## 1. Responsibility
The Verification Engine answers one question: *"Did we actually do what was planned and does it work?"* It sits between the Execution Engine and the Review Engine. It compares the raw generated artifacts against the `ExecutionPlan` and runs automated tests before subjective quality reviews begin.

## 2. Process & Heuristics
- **Requirements Tracing**: Maps every generated file back to a step in the `ExecutionPlan`. Ensures no unexpected files were created outside allowed bounded contexts.
- **Compilation Check**: Runs the project's build command (`tsc --noEmit`, `go build`).
- **Test Execution**: Runs the test suite targeting the modified files.
- **Coverage Check**: Guarantees that corresponding test files were generated alongside new source logic.

## 3. The Universal Contract
**Inputs**: `ExecutionPlan`, `ExecutionTrace` (Log of generated artifacts)
**Outputs**: `VerificationReport`
**Dependencies**: None.
**Failure Modes**: If verification fails (e.g., compile error, test failure), it routes the payload *back* to the Execution Engine for a correction loop, rather than passing it to the Review Engine.

## 4. Output Schema
```yaml
engine: "Verification"
status: "Failed"
confidence: 100
warnings:
  - "Step 2 (Generate Migration) was skipped by Execution."
  - "Compilation failed: Cannot find module 'lodash'."
next: "Execution" # Route back for correction loop
```
