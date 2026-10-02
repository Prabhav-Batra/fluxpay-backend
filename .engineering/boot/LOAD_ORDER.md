# Context Dependency Graph (LOAD_ORDER)

## Rationale
Context ingestion is not commutative. An AI Assistant cannot understand an architectural decision without first understanding the technology stack. It cannot execute a workflow without first understanding the engineering standards. 

This document enforces a strict, hierarchical load order. Each layer provides the vocabulary and constraints necessary to comprehend the subsequent layer.

## Sequence

### 1. Configuration (`config/*`)
**Description**: Project-specific variables and environment mappings.
**Dependency**: None.
**Output**: The foundational environment variables of the project.

### 2. Technology Stack (`config/stack.yaml`)
**Description**: The specific languages, frameworks, and tooling versions.
**Dependency**: Configuration.
**Output**: Syntactic and tooling constraints.

### 3. Engineering Standards (`standards/*`)
**Description**: Universal engineering rules (Security, Performance, Error Handling).
**Dependency**: Technology Stack.
**Output**: Execution constraints and quality baselines.

### 4. Architecture (`config/architecture.yaml`)
**Description**: System design, topology, and Architecture Decision Records (ADRs).
**Dependency**: Engineering Standards.
**Output**: Structural boundaries and component relationships.

### 5. Project Context (`memory/*`)
**Description**: The business domain, ubiquitous language, and current project state.
**Dependency**: Architecture.
**Output**: Semantic understanding of business logic.

### 6. Workflows (`workflows/*`)
**Description**: Standard Operating Procedures for active engineering tasks.
**Dependency**: Project Context.
**Output**: Step-by-step execution guides for the Assistant.

### 7. Templates (`templates/*`)
**Description**: Reusable boilerplate for documentation and code.
**Dependency**: Workflows.
**Output**: Output formatting rules.

### 8. Review Rules (`reviews/*`)
**Description**: Quality assurance checklists and acceptance criteria.
**Dependency**: Templates.
**Output**: Verification logic for completed tasks.
