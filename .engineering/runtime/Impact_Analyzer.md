# Impact Analyzer

## Purpose
Predicts the "blast radius" of a proposed code change before execution. It answers the question: "If I change X, what else might break?"

## Inputs
- `ParsedIntent` (JSON): The proposed changes (e.g., "Modify User schema").
- `DependencyGraphData` (Graph): The current state of dependencies.

## Outputs
- `ImpactReport` (JSON):
  ```json
  {
    "target_module": "user-repository",
    "blast_radius": {
      "direct_dependents": ["auth-service", "billing-service"],
      "transitive_dependents": ["api-gateway"]
    },
    "risk_score": "High",
    "breaking_change_likely": true,
    "recommended_actions": [
      "Run full integration test suite on billing-service",
      "Require Architecture Review"
    ]
  }
  ```

## Algorithm Steps
1. **Target Identification**: Map the `ParsedIntent` to specific nodes in the `DependencyGraphData`.
2. **Reverse Traversal**: Traverse the graph backwards (from target to incoming edges) to find all direct dependents.
3. **Transitive Traversal**: Continue the reverse traversal to find all components that indirectly depend on the target.
4. **Risk Scoring**:
   - **Low**: Target has 0-1 dependents, or is a pure frontend UI change.
   - **Medium**: Target has 2-5 dependents within the same bounded context.
   - **High**: Target is a core shared library, database schema, or public API contract with cross-context dependents.
5. **Report Generation**: Compile the affected modules and calculate the risk score.

## Integration
- The Impact Report is passed to the **Context Resolver** to ensure that tests for all affected dependents are loaded into context.
- Passed to the **Review Engine** to mandate stricter reviews (e.g., if Risk is High, mandate `architecture.md` review).

## Edge Cases & Error Handling
- **Graph Unavailability**: If the dependency graph failed to build, the Impact Analyzer defaults to a "High" risk score to enforce maximum caution.
