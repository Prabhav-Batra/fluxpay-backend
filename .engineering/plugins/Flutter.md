# Flutter Plugin

## Registration
- ID: `projectos.plugins.flutter`
- Version: `1.0.0`
- Compatibility: `Flutter 3.x`, `Dart 3.x`

## Capabilities
- **Standards**: Injects Dart/Flutter specific rules covering state management, rendering performance, and widget architecture.
- **Templates**: Provides `flutter-feature-module`.
- **Workflows**: Hooks into `flutter build apk` or `flutter build ios`.
- **Knowledge Packs**: Injects the `flutter` tech pack from the registry.

## Core Rules
1. **State Management**: BLoC or Riverpod must be used for global state. `setState` is only permitted for ephemeral, local UI state (e.g., toggling a checkbox, expanding a tile).
2. **Widget Composition**: Break large `build` methods into smaller, stateless widgets rather than helper methods returning `Widget`. This leverages Flutter's element tree diffing efficiently.
3. **Const usage**: Always use the `const` keyword for widgets that don't change at runtime to prevent unnecessary rebuilds. Enable the `prefer_const_constructors` lint rule.
4. **Platform Channels**: All native platform integrations (MethodChannels) must be isolated behind an interface in the infrastructure layer. Never call a MethodChannel directly from a UI widget.

## Common Anti-Patterns
- ❌ Putting business logic or API calls inside a `StatefulWidget`'s `build` or `initState` method.
- ❌ Passing `BuildContext` across async gaps without checking `if (!context.mounted) return;`.
- ❌ Using `ListView` for massive lists instead of `ListView.builder`.

## Dependencies
- Requires: Flutter SDK.
- Links to Pack: `registry/packs/flutter/manifest.yaml`
