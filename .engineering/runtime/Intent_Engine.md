# Intent Engine

## Purpose
Parses unstructured human language into semantic constraints and actionable engineering intent. This is the first cognitive module in the pipeline.

## Inputs
- `UserRequest` (String): Raw input from the user (e.g., "Add a login button that uses Google OAuth").
- `ChatHistory` (Array of Strings): Prior conversational context to resolve pronouns or implicit references.

## Outputs
- `ParsedIntent` (JSON):
  ```json
  {
    "primary_action": "add_feature",
    "target_domain": "authentication",
    "explicit_constraints": ["use Google OAuth"],
    "implicit_assumptions": ["requires frontend UI update", "requires backend route for OAuth callback"],
    "confidence_score": 92
  }
  ```

## Algorithm Steps
1. **Entity Extraction**: Identify nouns that map to domain concepts (e.g., "button" -> UI Component, "login" -> Auth Domain).
2. **Action Classification**: Map the core verb to an engineering action (Create, Modify, Delete, Refactor, Debug, Explain).
3. **Constraint Identification**: Extract any explicit technical boundaries ("must be fast", "use Redis", "don't touch the DB").
4. **Ambiguity Check**: If the `confidence_score` is below 75, the engine must pause and ask the user a clarifying question before proceeding to the Task Classification Engine.

## Edge Cases & Error Handling
- **Ambiguous Request**: User says "fix the bug". Engine emits `AmbiguousIntentException`. Fallback: Ask user "Which bug? Please provide an error message or ticket number."
- **Contradictory Intent**: User says "build a stateless app that stores sessions in memory". Engine identifies the contradiction. Fallback: Ask user to clarify architectural intent.
- **Out of Scope**: User asks the engine to write a marketing email. Engine identifies `target_domain: null`. Fallback: Remind user this is an engineering context.
