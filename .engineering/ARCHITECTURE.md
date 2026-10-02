# ProjectOS Architecture Constitution (v1.0.0)

> **STATUS: STABLE (CORE FROZEN)**

## 1. Vision & Engineering Design Principles
ProjectOS is an extensible, language-agnostic Engineering Operating Framework. Future contributions must adhere to these 9 core principles:
1. Configuration over prompts.
2. Context over conversation history.
3. Composition over monoliths.
4. Explicit standards over implicit assumptions.
5. Validation before execution.
6. Documentation as executable knowledge.
7. Plugins over core modifications.
8. Deterministic workflows over ad hoc reasoning.
9. Human approval at critical decision points.
10. No AI may generate a "God File". An Engineering Manifest MUST be approved before implementation.
11. **The Core Evolution Rule**: Does a proposal make developers measurably more productive, or is it just another interesting idea? If it does not improve productivity, quality, maintainability, or reliability, it does not go into the core. It becomes an RFC or a plugin.

## 2. Runtime Architecture (Staged Pipeline)
As ProjectOS scales, the cognitive runtime is organized into distinct **Processing Stages**. Cycles are strictly forbidden.

### Stage 1: Discovery & Intent
- **Intent Engine**: Parses human language into semantic constraints and intent.
- **Task Classification Engine**: Determines task type, priority, and complexity.
- **Technology Resolution Engine**: Identifies implicit and explicit technologies, generating the Technology Graph.
- **Context Resolver**: Determines exactly which configuration, standards, and memory files must be loaded.

### Stage 2: Policy & Strategy
- **Policy Engine**: Evaluates the Context against `config/constraints.yaml`. Halts execution if a hard limit is breached.
- **Engineering Planning Engine**: Synthesizes the context into a step-by-step `ExecutionPlan`.

### Stage 3: Action & Validation
- **Execution Engine**: Produces code or artifacts strictly following the `ExecutionPlan`.
- **Verification Engine**: Traces the generated artifacts back to the `ExecutionPlan` to ensure requirements were met.

### Stage 4: Assurance & Delivery
- **Review Engine**: Evaluates the verified output against the loaded Standards.
- **Output Engine**: Formats the final deliverables.

## 3. Core Modules (Frozen for v1.0)
The following modules are permanently locked. Any changes require an RFC.
- `boot`
- `runtime`
- `registry`
- `standards`
- `workflows`
- `templates`
- `reviews`
- `memory`
- `config`

## 4. The Universal Engine Contract
Every engine MUST implement this standard contract:
- **Purpose**: Singular responsibility.
- **Inputs**: Exact data schemas consumed.
- **Outputs**: Structured artifact produced (never raw text).
- **Dependencies**: External modules relied on.
- **Failure Modes**: Strict error handling definitions.
- **Confidence**: Numeric score (0-100).
- **Extension Points**: Hooks for Plugins.
- **Observability**: Trace metadata explaining the decision.

## 5. Plugin System Architecture
ProjectOS Core is immutable. Technology-specific behavior is introduced via **Plugins** inside `.engineering/plugins/`.

## 6. Stability Levels
Every module must declare its maturity:
- `experimental`: Safe to break.
- `stable`: Core Frozen, breaking changes require Major version bump.
- `deprecated`: Scheduled for removal.
- `planned`: Accepted RFC, not yet implemented.

## 7. Semantic Versioning Policy
ProjectOS adheres strictly to SemVer (`vX.Y.Z`).
