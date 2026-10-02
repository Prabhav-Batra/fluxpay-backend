# Python Pitfalls

1. ❌ **Mutable Default Arguments**: Never use `[]` or `{}` as a default argument (`def func(items=[])`). It evaluates once at function definition, causing state to leak across calls. Use `items=None`.
2. ❌ **Broad Exception Catching**: Catching `except Exception:` masks bugs and makes debugging impossible. Always catch specific exceptions (e.g., `except KeyError:`).
3. ❌ **Global State**: Avoid module-level global variables. They break unit test isolation and cause race conditions in concurrent environments.
4. ❌ **Blocking the Event Loop**: Calling `time.sleep()` or synchronous database drivers in an `async def` function will freeze the entire async application.
5. ❌ **Ignoring `__init__.py`**: Missing `__init__.py` files can break package resolution and imports in older tools.
6. ❌ **String Concatenation in Loops**: Using `+=` to build large strings is extremely slow due to immutability. Use `''.join(list_of_strings)`.
7. ❌ **Leaking Secrets in Tracebacks**: Default exception handlers can dump local variables to logs, potentially exposing API keys. Use specialized loggers to sanitize tracebacks.
8. ❌ **Overusing Metaclasses**: "Magic" metaclasses make code incredibly difficult to read and maintain for junior engineers. Prefer composition or decorators.
