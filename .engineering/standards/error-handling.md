# Global Standard: Error Handling

## Philosophy
1. **Errors are values**: Treat errors as first-class citizens in your application. Handle them explicitly rather than relying heavily on unchecked exceptions, where the language permits (e.g., Go, Rust, or returning Results in TS).
2. **Never swallow errors**: Catching an error and doing nothing (or just logging it without taking action) is strictly prohibited. If you catch it, you must handle it, wrap it, or return it.

## Error Structure
3. **Internal Error Types**: Create a unified internal error type for your domain. It should include:
   - `Code`: A machine-readable string (e.g., `USER_NOT_FOUND`)
   - `Message`: A human-readable description for developers (e.g., "User with ID 123 not found in database")
   - `Context`: Key-value pairs of relevant data (e.g., `{ "userId": 123 }`)
   - `Cause`: The original underlying error (if wrapping)
4. **Distinguish Domain vs. Technical Errors**: 
   - *Domain Errors* (e.g., InsufficientFunds) are expected and should be returned cleanly to the caller.
   - *Technical Errors* (e.g., DatabaseConnectionTimeout) are unexpected and should trigger alerts.

## API Error Responses
5. **Hide implementation details**: Never leak stack traces, database query strings, or internal library errors to external API clients.
6. **Standardized envelope**: (See `api.md`) All HTTP error responses must use a consistent JSON envelope containing `code`, `message`, and `traceId`.

## Wrapping & Propagation
7. **Wrap at boundaries**: When an error crosses a boundary (e.g., from Repository to Service), wrap it with additional context. Don't just return `sql.ErrNoRows`; return `UserNotFound` wrapping `sql.ErrNoRows`.
8. **Preserve root causes**: When wrapping errors, ensure the original stack trace or root cause is preserved for logging purposes.

## Logging Errors
9. **Log at the top level**: Avoid logging the same error at every layer of the call stack. Let the error propagate and log it once at the highest appropriate level (e.g., the Controller or a global error handler), along with its full context.
10. **Include Correlation IDs**: Every logged error must include the request's correlation ID / trace ID.
