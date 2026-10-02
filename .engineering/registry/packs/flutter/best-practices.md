# Flutter Best Practices

1. **State Management**: Use a predictable state management solution (Riverpod or BLoC) globally. Avoid using `setState` for anything other than ephemeral, local UI animation state.
2. **Widget Composition**: Break massive UI files into small, stateless custom widgets. A `build()` method should rarely exceed 60 lines.
3. **Immutable State**: Always use `@immutable` classes and `freezed` or `equatable` for state objects to ensure predictable equality comparisons.
4. **Theme Extraction**: Never hardcode colors or text sizes (`Colors.red` or `TextStyle(fontSize: 16)`). Extract them to `ThemeData` and use `Theme.of(context)`.
5. **Localization**: Use `AppLocalizations` for all strings from day one. Hardcoding user-facing English strings makes translation refactoring a nightmare.
6. **Async Error Handling**: Use a functional error handling approach (e.g., `fpdart`'s `Either` type) for repositories to avoid throwing exceptions that crash the app.
7. **Responsive Layouts**: Use `LayoutBuilder` and `MediaQuery` or responsive packages to ensure the UI scales correctly on tablets and web, not just mobile phones.
8. **Dependency Injection**: Use `get_it` or Riverpod to inject dependencies (like Repositories) into controllers, allowing for easy mocking during tests.
9. **Const Constructors**: Use `const` everywhere possible. It drastically reduces memory allocation and garbage collection overhead during UI rebuilds.
10. **Linting**: Enforce `flutter_lints` and consider adding strict rules from `very_good_analysis`.
