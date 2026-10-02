# Knowledge Graph

## Purpose
Stores architectural facts and domain knowledge derived from the codebase and documentation. While the Dependency Graph tracks structural code relations, the Knowledge Graph tracks semantic concepts (e.g., "The Billing Service uses Stripe").

## Inputs
- Project Documentation (`.engineering/memory/`, `README.md`).
- Codebase (comments, specific annotations).

## Outputs
- `SemanticTriples` (JSON/RDF):
  ```json
  [
    { "subject": "BillingService", "predicate": "integratesWith", "object": "StripeAPI" },
    { "subject": "UserAuth", "predicate": "implementedUsing", "object": "JWT" },
    { "subject": "Database", "predicate": "requires", "object": "MigrationTool" }
  ]
  ```

## Algorithm Steps
1. **Extraction Pipeline**: Scan markdown files in `memory/adr/` and `memory/domain/`.
2. **NLP Processing**: Extract Subjects, Predicates, and Objects using standard NLP entity extraction techniques.
3. **Graph Construction**: Build the directed semantic graph where nodes are concepts and edges are the predicates.
4. **Query Interface**: Allow the Context Resolver to query semantic relationships (e.g., `Query(subject: "UserAuth", predicate: "implementedUsing") -> "JWT"`).

## Usage in Pipeline
- When the Intent Engine parses "Update the payment gateway", it queries the Knowledge Graph for "payment gateway". The graph returns "StripeAPI" and "BillingService", instantly giving the AI precise context without needing to search the codebase.

## Edge Cases & Error Handling
- **Contradictory Facts**: If an ADR states "Use PostgreSQL" but code extraction finds "MongoDB", the graph must flag the contradiction and emit a warning to the `Execution_Engine` regarding documentation drift.
