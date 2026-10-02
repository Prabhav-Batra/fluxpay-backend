# Cognitive Dependency Resolution Engine

## The Resolution Algorithm
During the STARTUP phase (specifically Phase 2: Mount Stack), the AI Assistant must cognitively resolve all technology dependencies declared in `config/stack.yaml`. This ensures that if the project uses a high-level framework (like Spring Boot), the AI automatically inherits the rules for its underlying foundation (like Java and Maven).

## Execution Protocol

1. **Read Stack**: Identify all root technologies listed in `.engineering/config/stack.yaml`.
2. **Initialize Graph**: Create an empty list of `resolved_tech_packs`.
3. **Traverse (Recursive)**:
   For each technology `T`:
   - If `T` is already in `resolved_tech_packs`, skip (prevents cyclic loops).
   - Read `.engineering/registry/packs/T/manifest.yaml`.
   - Add `T` to `resolved_tech_packs`.
   - For every dependency `D` listed in `T.spec.dependencies`, recursively call Traverse(`D`).
4. **Flatten Context**:
   - Once traversal is complete, iterate through `resolved_tech_packs`.
   - For each pack, load all explicit assets defined in `manifest.yaml` (e.g., `standards.md`, `best-practices.md`) into active memory.
5. **Conflict Resolution**:
   - If two packs define conflicting standards (e.g., formatting), the child pack (the root technology from `stack.yaml`) overrides the parent dependency.

*By executing this algorithm cognitively, the AI guarantees that no underlying standard is missed, even if not explicitly defined by the human engineers in the root configuration.*
