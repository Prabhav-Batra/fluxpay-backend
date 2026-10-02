# Initialization Routine (STARTUP)

## Execution Protocol
This document defines the strict `init` sequence for the AI Assistant. Upon activation, the Assistant must execute these phases sequentially. Proceeding to a subsequent phase is prohibited until the current phase is fully resolved.

### Phase 0: Cognitive Reset
- Purge all implicit assumptions regarding frameworks, languages, and project structures.
- Acknowledge the boundaries of the host repository.
- Adopt the cognitive framework defined in `THINKING_MODEL.md`.

### Phase 1: Environment Resolution
- Locate the `.engineering/` root directory.
- Verify the presence of core OS components (`config`, `context`, `standards`).
- If core components are missing, halt and notify the user of a corrupted OS state.

### Phase 2: Sequential Ingestion
Execute the ingestion routine strictly according to `LOAD_ORDER.md`.

1. **Mount Config**: Load project metadata and environmental constraints.
2. **Mount Stack**: Load the required technology stack and language semantics.
3. **Mount Standards**: Load engineering guidelines (security, API, database).
4. **Mount Architecture**: Load system design boundaries and ADRs (Architecture Decision Records).
5. **Mount Context**: Load domain logic and ubiquitous language.
6. **Mount Workflows**: Load task-specific Standard Operating Procedures (SOPs).
7. **Mount Templates**: Index available boilerplate structures.
8. **Mount Reviews**: Load code review and acceptance criteria.

### Phase 3: State Verification
- Cross-reference loaded configurations. (e.g., Ensure the technology stack aligns with the defined architecture).
- Acknowledge successful boot sequence to the user.

### Phase 4: Ready State
- The Assistant is now in `READY` state.
- Transition from initialization mode to execution mode.
- Await user engineering requests.
