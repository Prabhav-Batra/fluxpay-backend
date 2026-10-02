# Repository Scanner

## Purpose
Recursively scans the target repository to build an abstract syntax tree and file topology map. This serves as the foundational data gathering step for the Intelligence Engine.

## Inputs
- Repository URI (String)
- `.gitignore` and `.projectosignore` file rules.

## Outputs
- `RawTopologyData` (JSON):
  ```json
  {
    "repository_name": "billing-service",
    "root_uri": "file:///src/billing-service",
    "total_files_scanned": 142,
    "directory_tree": {
      "src": {
        "domain": ["Invoice.ts", "Customer.ts"],
        "api": ["routes.ts"]
      }
    },
    "file_metadata": [
      {
        "path": "src/domain/Invoice.ts",
        "language": "typescript",
        "size_bytes": 4096,
        "imports": ["./Customer.ts", "uuid"]
      }
    ]
  }
  ```

## Algorithm Steps
1. **Rule Initialization**: Load `.gitignore`, `.projectosignore`, and global ignore patterns (e.g., `node_modules`, `.git`, `dist`).
2. **Directory Traversal**: Perform a Breadth-First Search (BFS) from the repository root, skipping any directories or files that match the ignore rules.
3. **Language Detection**: Map file extensions to languages (e.g., `.ts` -> TypeScript, `.go` -> Go, `.yaml` -> YAML).
4. **Metadata Extraction**: For each valid file:
   - Calculate file size.
   - Use language-specific heuristics (regex or lightweight AST parsing) to extract `import` or `require` statements.
   - Identify exported symbols (classes, functions, types).
5. **Topology Generation**: Aggregate the file metadata into the hierarchical `RawTopologyData` JSON structure.

## Dependencies
- File system read access.
- Lightweight language parsers (e.g., Tree-sitter or regex fallback).

## Edge Cases & Error Handling
- **Massive Repository**: If `total_files_scanned` exceeds 10,000, the scanner must halt and request sub-directory scoping to prevent OOM errors.
- **Deeply Nested Directories**: Restrict BFS depth to 15 levels to prevent infinite recursion from symlinks.
