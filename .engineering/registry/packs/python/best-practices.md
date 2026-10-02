# Python Best Practices

1. **Strict Typing**: Use type hints everywhere (`def func(a: int) -> str:`). Run `mypy` or `pyright` in strict mode in CI.
2. **Dependency Management**: Never use raw `pip` globally. Always use `poetry`, `pipenv`, or `uv` to manage environments and lockfiles.
3. **Formatter & Linter**: Enforce `black` for formatting and `ruff` for linting in a pre-commit hook.
4. **Async/Await Context**: Use `asyncio` for I/O-bound microservices (like FastAPI). Never mix synchronous blocking calls (`requests`) in an async event loop; use `httpx` instead.
5. **Context Managers**: Always manage file, network, and database resources using `with` blocks to guarantee cleanup.
6. **Dataclasses vs Dicts**: Prefer `dataclasses` or `pydantic` models over raw dictionaries for structured data to ensure type safety.
7. **Environment Variables**: Use `pydantic-settings` to load and validate environment variables on startup.
8. **Logging**: Use the standard `logging` library or `structlog` for structured JSON logs. Do not use `print()` in production code.
9. **Path Management**: Use `pathlib.Path` instead of string manipulation with `os.path`.
10. **Exception Handling**: Create custom exception classes for domain errors rather than throwing generic `Exception` or `ValueError`.
