# Dependency Graph

## Purpose
Maps the structural relationships between internal modules (e.g., Auth -> User -> DB) and external packages (e.g., Maven dependencies). Critical for architectural validation and impact analysis.

## Inputs
- `RawTopologyData` (JSON): From the Repository Scanner, containing import statements.
- Package manager files (`package.json`, `pom.xml`, `go.mod`).

## Outputs
- `DependencyGraphData` (JSON/Graph):
  ```json
  {
    "nodes": [
      { "id": "auth-service", "type": "module" },
      { "id": "user-repository", "type": "module" },
      { "id": "express", "type": "external_package" }
    ],
    "edges": [
      { "source": "auth-service", "target": "user-repository", "relation": "imports" },
      { "source": "auth-service", "target": "express", "relation": "depends_on" }
    ]
  }
  ```

## Algorithm Steps
1. **Node Creation**: Create a node for every internal module identified in the topology, and every external dependency found in package manager files.
2. **Edge Construction**: Iterate through the import metadata in `RawTopologyData`. For every import, draw a directed edge from the importing module to the imported module.
3. **Cycle Detection**: Run a Depth-First Search (DFS) across the constructed graph. If a back-edge is found (A -> B -> C -> A), flag it as a circular dependency.
4. **Layer Assignment**: Assign depth layers to nodes based on topological sorting. Modules with zero outgoing edges (e.g., pure domain models) are Layer 0.

## Enforcement Rules
- If the DFS detects a cycle, emit a `CircularDependencyException` to the Policy Engine.
- If a high-level module (e.g., Presentation) directly depends on a low-level implementation (e.g., Database Driver) bypassing the Application layer, flag an architecture violation based on `Layer_Rules.md`.

## Edge Cases & Error Handling
- **Dynamic Imports**: Language features like runtime `require()` or reflection cannot be statically mapped. Fallback: Log a warning indicating partial graph accuracy.
