# Flutter Feature Module Template

This directory provides the canonical structure for a new feature module in a Flutter application using a layered architecture.

```text
lib/
└── features/
    └── {{ FEATURE_NAME }}/
        ├── domain/                  # Core logic, independent of Flutter
        │   ├── entities/            # Business objects (e.g., User, Product)
        │   ├── repositories/        # Abstract repository interfaces
        │   └── usecases/            # Encapsulated business actions
        ├── data/                    # Data layer (implements domain interfaces)
        │   ├── models/              # DTOs and JSON serialization
        │   ├── repositories/        # Repository implementations
        │   └── datasources/         # APIs (Remote) and Databases (Local)
        │       ├── {{ FEATURE_NAME }}_remote_data_source.dart
        │       └── {{ FEATURE_NAME }}_local_data_source.dart
        └── presentation/            # Flutter UI and State
            ├── pages/               # Full screen widgets
            ├── widgets/             # Reusable UI components for this feature
            └── manager/             # State management (BLoC, Riverpod, Provider)
                ├── {{ FEATURE_NAME }}_state.dart
                └── {{ FEATURE_NAME }}_controller.dart
```

## Rules for this structure:

1. **Domain layer**: Must not import `package:flutter` or any third-party libraries (except pure Dart utilities like `equatable` or `dartz`).
2. **Data layer**: Depends on the Domain layer. Maps raw JSON to Data Models, then Data Models to Domain Entities.
3. **Presentation layer**: Depends on the Domain layer (via Usecases or Repositories). The UI reacts to State emitted by the State Manager.
4. **No cross-feature coupling**: `feature_a` should not directly import from `feature_b/data`. If features must communicate, they do so through the app-level dependency injection or shared domain events.
