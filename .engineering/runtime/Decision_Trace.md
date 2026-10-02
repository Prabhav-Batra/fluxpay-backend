# Decision Trace Engine

## Purpose
Tracks the complete cognitive lifecycle of every engineering task for auditing and debugging. It acts as the "flight data recorder" for the AI's execution pipeline.

## Inputs
- Event streams from all other Runtime Engines (Intent, Classify, Resolve, Execute, Verify, Output).

## Outputs
- Immutable trace logs persisted to disk (e.g., `.projectos/traces/{taskId}.jsonl`).

## Schema
Each event in the trace follows this schema:
```json
{
  "timestamp": "2026-07-01T14:00:00Z",
  "engine": "Context_Resolver",
  "action": "Load_Standard",
  "target": "standards/security.md",
  "rationale": "Task matches 'auth' domain; security standard is mandatory.",
  "inputs": { "task_id": 123 },
  "outputs": { "context_size_bytes": 4096 }
}
```

## Algorithm Steps
1. **Event Interception**: The Trace Engine hooks into the core event bus of the pipeline.
2. **Standardization**: Every raw event is mapped to the standard trace schema. The `rationale` field is mandatory for any decision that branches logic or modifies files.
3. **Persistence**: Events are appended to a JSON Lines (JSONL) file specific to the current task execution. JSONL ensures that if the process crashes, the trace up to the crash point is preserved.

## Usage & Integration
- **Debugging**: If the AI hallucinates or generates a "God File", human engineers can read the trace to see exactly which engine failed (e.g., Did the Context Resolver fail to load the boundary rules? Did the Execution Engine ignore them?).
- **Feedback Loop**: Traces are fed into the Explainability Engine to translate raw JSON into human-readable summaries.

## Edge Cases & Error Handling
- **Disk Full**: If the trace cannot be written to disk, the Trace Engine must log to standard error and allow the execution to continue. Tracing failure should not block feature delivery.
