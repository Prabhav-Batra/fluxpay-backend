# ProjectOS Plugin Specification

## Overview
A Plugin in ProjectOS is a modular extension that adds domain knowledge, technology constraints, or custom workflows to the AI's runtime engine. 

## Plugin Structure
A valid plugin resides in `.engineering/plugins/` as a markdown file (`[PluginName].md`) and optionally links to a tech pack in `registry/packs/`.

### Required Sections
1. **Registration**: Contains `ID`, `Version`, and `Compatibility`. Used by the Context Loader to determine applicability.
2. **Capabilities**: Lists what the plugin injects (`Standards`, `Templates`, `Workflows`, `Knowledge Packs`).
3. **Core Rules**: 3-5 absolute engineering rules specific to this technology.
4. **Common Anti-Patterns**: 2-3 specific "What NOT to do" examples for the AI to avoid.
5. **Dependencies**: Links to actual tech packs (`manifest.yaml`).

## The Lifecycle Hooks
Plugins can hook into the ProjectOS Runtime Engine at different stages:
1. **Pre-Planning**: Hook into `Intent_Engine` to modify how user requests are interpreted (e.g., "Always assume React means Next.js in this repo").
2. **Context Aggregation**: Hook into `Context_Resolver` to inject custom config files (e.g., always load `tailwind.config.js`).
3. **Pre-Execution**: Hook into `Policy_Engine` to enforce technology-specific hard constraints.
4. **Post-Execution**: Hook into `Verification_Engine` to run custom linters or tests.

## Development Rules
- Plugins must be declarative markdown. Do not put executable Python/Bash scripts inside the plugin markdown file.
- Plugins should augment, not override, the core `ARCHITECTURE.md`.
