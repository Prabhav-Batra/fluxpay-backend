# CLI Command Architecture

## Design
The `projectos` CLI acts as a local proxy to the Framework Runtime.

## Core Commands
- `init`: Bootstraps `.engineering/` in a new repo.
- `doctor`: Validates configuration integrity.
- `validate`: Runs the Validation Platform on the codebase.
- `scan`: Runs the Repository Scanner.
- `review`: Invokes the Review Engine.
- `plugin`: Manages plugins (`install`, `list`).
- `context`: Shows current resolved context.
- `workflow`: Runs a specific workflow state machine.
- `generate`: Triggers Code Generation (requires Manifest).
- `upgrade`: Upgrades ProjectOS core files.
