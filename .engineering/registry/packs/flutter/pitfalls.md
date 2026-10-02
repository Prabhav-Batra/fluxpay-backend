# Flutter Pitfalls

1. ❌ **God Widgets**: Putting business logic, API calls, and UI rendering inside a single `StatefulWidget`. This makes the widget completely untestable.
2. ❌ **Unbounded Height in ListViews**: Nesting a `ListView` inside a `Column` without wrapping it in an `Expanded` or giving it a fixed height will crash with layout exceptions.
3. ❌ **Ignoring `BuildContext` Lifecycle**: Storing a `BuildContext` across async gaps (`await`) and using it later without checking `if (mounted)`. This will crash the app if the user navigated away.
4. ❌ **Overusing `setState`**: Calling `setState` at the root of a complex screen re-renders the entire tree. Rebuild only the leaves that change.
5. ❌ **Blocking the UI Thread**: Performing heavy JSON parsing, image processing, or cryptography on the main thread. Always use `compute()` or `Isolates` for heavy lifting.
6. ❌ **Memory Leaks from Controllers**: Forgetting to call `.dispose()` on `TextEditingController`, `AnimationController`, or `ScrollController` in the `dispose()` method of a `StatefulWidget`.
7. ❌ **Hardcoded UI Strings**: Directly placing English strings in Text widgets, bypassing the `.arb` translation files.
8. ❌ **Nested Callbacks**: Creating "callback hell" in UI code for handling dialogs and navigation rather than using a centralized router (like `go_router`).
