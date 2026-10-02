# Technology Plugin Template

*Copy this template to `.engineering/plugins/[TechnologyName].md` to register a new framework, language, or tool.*

---

# [Technology Name] Plugin

## Registration
- ID: `projectos.plugins.[technology_name_lowercase]`
- Version: `1.0.0`
- Compatibility: `[Target Version/Environment]`

## Capabilities
- **Standards**: Injects [Technology]-specific rules covering [Area 1] and [Area 2].
- **Templates**: (Optional) Provides `[template-folder-name]`.
- **Workflows**: (Optional) Hooks into `[specific-workflow]`.
- **Knowledge Packs**: Injects the `[pack-name]` tech pack from the registry.

## Core Rules
1. **[Rule 1 Title]**: [Clear, actionable rule that the AI must follow when writing code in this technology].
2. **[Rule 2 Title]**: [Clear, actionable rule].
3. **[Rule 3 Title]**: [Clear, actionable rule].

## Common Anti-Patterns
- ❌ [Describe a common mistake developers make with this technology].
- ❌ [Describe a dangerous security or performance pitfall].

## Dependencies
- Requires: [SDK/CLI/Environment].
- Links to Pack: `registry/packs/[pack-name]/manifest.yaml` (If applicable).
