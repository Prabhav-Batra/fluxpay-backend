# Migration Guide

## Adopting ProjectOS in Existing Repos
1. Run `./projectos.sh init`.
2. Add `.engineering/` to your version control.
3. Map your existing stack to ProjectOS plugins in `project.yaml`.
4. Ensure your existing code is gradually refactored to pass `make validate`.
