# Java Best Practices

1. **Immutability**: Prefer immutable objects. Use `record` classes (Java 14+) for DTOs and Data Models to automatically get immutability, `equals()`, `hashCode()`, and `toString()`.
2. **Null Safety**: Avoid returning `null`. Use `Optional<T>` as the return type for methods that might not return a value. Do not use `Optional` as a field type or method parameter.
3. **Streams API**: Use Java Streams for collection transformations and filtering rather than writing verbose `for` loops.
4. **Dependency Injection**: Design classes to receive their dependencies via constructor injection, making them easily testable without a framework.
5. **Var Keyword**: Use `var` (Java 10+) for local variables when the type is obvious from the right-hand side of the assignment, improving readability.
6. **Exception Handling**: Use specific, custom RuntimeExceptions for business errors rather than checked exceptions (`throws Exception`), which pollute method signatures up the call stack.
7. **String Formatting**: Use `String.format()` or Java 15+ Text Blocks (`"""`) rather than concatenating strings with `+` in loops or complex layouts.
8. **Concurrency**: Prefer the `java.util.concurrent` package (Executors, CompletableFuture) over raw `Thread` creation.
9. **Naming**: Adhere strictly to camelCase for variables/methods, PascalCase for classes, and UPPER_SNAKE_CASE for constants.
10. **Lombok**: If using Lombok, restrict its use to `@Data`, `@Builder`, and `@Slf4j`. Avoid complex features that obscure bytecode generation.
