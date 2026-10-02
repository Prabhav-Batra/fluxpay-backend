# Java Pitfalls

1. ❌ **NullPointerException (NPE)**: Failing to check for null before accessing object methods. Always use `Objects.requireNonNull()` at boundaries or `@NonNull` annotations.
2. ❌ **Resource Leaks**: Leaving InputStreams, Database Connections, or Sockets open. Always use `try-with-resources` (`try (var stream = new FileInputStream(file)) { ... }`).
3. ❌ **Swallowing Exceptions**: Catching an exception and logging it without re-throwing it or returning an error state, causing silent failures further down the line.
4. ❌ **String Equality**: Using `==` to compare strings instead of `.equals()`.
5. ❌ **God Classes**: Creating massive utility classes (`StringUtil`, `DateUtil`) with hundreds of static methods rather than placing behavior on the domain objects themselves.
6. ❌ **Mutable Static Fields**: Using `public static` fields that are not `final`. This creates global state that breaks concurrent applications.
7. ❌ **Overusing Checked Exceptions**: Forcing callers to catch exceptions they cannot possibly recover from. Use RuntimeExceptions.
8. ❌ **Ignoring Thread Safety**: Using `ArrayList` or `HashMap` in a multi-threaded context without synchronization. Use `ConcurrentHashMap` or `CopyOnWriteArrayList`.
