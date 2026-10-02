# Architecture Decision Record (ADR) Template

## Title: [ADR-00X: Short Noun Phrase Describing the Decision]

**Status**: [Proposed | Accepted | Rejected | Superseded by ADR-XXX]
**Date**: [YYYY-MM-DD]
**Authors**: [Names or AI Persona]

## 1. Context and Problem Statement
[Describe the context and problem statement, e.g., in free form using two to three sentences. What is the issue we're seeing? What is the business driver? Why must a decision be made now?]

## 2. Decision Drivers (Forces)
* [Driver 1, e.g., a force, facing concern, ...]
* [Driver 2, e.g., a force, facing concern, ...]
* [Driver 3, e.g., a force, facing concern, ...]

## 3. Considered Options
* [Option 1: e.g., Use PostgreSQL]
* [Option 2: e.g., Use MongoDB]
* [Option 3: e.g., Use Redis]

## 4. Decision Outcome
Chosen option: **[Option 1]**, because [justification. e.g., only option 1 meets our strict consistency requirements, and we already have operational expertise].

### Positive Consequences
* [e.g., strong ACID guarantees]
* [e.g., simple relational queries]

### Negative Consequences
* [e.g., horizontal scaling is harder than Option 2]
* [e.g., requires rigid schema definitions up front]

## 5. Pros and Cons of the Options

### [Option 1]
* ✅ Good, because [argument]
* ✅ Good, because [argument]
* ❌ Bad, because [argument]

### [Option 2]
* ✅ Good, because [argument]
* ❌ Bad, because [argument]
* ❌ Bad, because [argument]

## 6. Implementation Notes / References
* [Link to PoC PR if any]
* [Link to relevant documentation or benchmark]
