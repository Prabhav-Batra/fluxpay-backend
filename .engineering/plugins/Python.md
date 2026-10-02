# Python Plugin

## Registration
- ID: `projectos.plugins.python`
- Version: `1.0.0`
- Compatibility: `Python 3.10+`

## Capabilities
- **Standards**: Injects Python specific rules covering async patterns, typing, and dependency management.
- **Workflows**: Hooks into `pytest` and `pip`/`poetry`.
- **Knowledge Packs**: Injects the `python` tech pack from the registry.

## Core Rules
1. **Typing**: Type hints are mandatory (`def foo(a: int) -> str:`). Use `mypy` or `pyright` in strict mode to enforce them.
2. **Environment Management**: Never install dependencies globally. Use `poetry`, `pipenv`, or virtual environments (`venv`).
3. **Formatting**: Code must be formatted using `Black` and linted with `Ruff` or `Flake8`.
4. **Async/Await**: When using `asyncio` (e.g., FastAPI), never block the event loop with synchronous I/O operations (like `requests` or `time.sleep()`). Use `httpx` and `asyncio.sleep()` instead.

## Common Anti-Patterns
- ❌ Using mutable default arguments (e.g., `def append_to(item, my_list=[]):`).
- ❌ Catching broad exceptions (`except Exception:`) without logging or re-raising.
- ❌ Not explicitly managing resources with context managers (`with open('file') as f:`).

## Dependencies
- Requires: Python 3.10+, Poetry/Pip.
- Links to Pack: `registry/packs/python/manifest.yaml`
