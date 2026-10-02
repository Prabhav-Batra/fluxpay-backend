# Policy Engine

## Purpose
The absolute authority in ProjectOS. It reads `constraints.yaml` and architectural rules to enforce physical boundaries that the AI cannot violate. It acts as a pre-execution firewall.

## Inputs
- `Engineering Manifest` (Proposed by the AI).
- `config/constraints.yaml`.
- `.engineering/standards/architecture/*.md`.

## Outputs
- `PolicyDecision`: Allow or Deny (with violation codes).

## Constraints Schema
Policies are composed of AND/OR rules.
- **Security Constraints**: E.g., `require_mfa_for_admin: true`.
- **License Constraints**: E.g., `forbidden_licenses: ["GPL", "AGPL"]`.
- **Architectural Constraints**: E.g., `layer_direction: inbound_only`.

## Algorithm Steps
1. **Manifest Parsing**: Read the proposed `Engineering Manifest`.
2. **Architecture Guard Evaluation**:
   - Check `File_Size_Rules.md`. If the manifest proposes appending 500 lines to a file already at 800 lines, **DENY (God File Risk)**.
   - Check `Module_Boundaries.md`. If `src/billing` tries to import `src/auth/internal`, **DENY (Boundary Violation)**.
3. **Constraint Evaluation**: Scan the manifest dependencies against `constraints.yaml.allowed_licenses`.
4. **Execution Gate**:
   - If any evaluation yields a DENY, the Execution Engine is halted instantly.
   - The AI is fed the `PolicyDecision` error code and forced to regenerate the Manifest.

## Override Auditing
- If a human explicitly overrides a Policy Engine denial (e.g., via CLI flag `--force-policy-override`), the engine logs the override to `memory/tech-debt/` with a high severity alert. The AI cannot override policies on its own.
