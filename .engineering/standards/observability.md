# Global Standard: Observability

## The Three Pillars
1. **Metrics (Is there a problem?)**: Aggregated data over time. Used for alerting and high-level dashboards.
2. **Logs (What is the problem?)**: Discrete, high-fidelity records of events. Used for deep-dive debugging.
3. **Traces (Where is the problem?)**: The lifecycle of a single request across multiple services. Used for performance bottleneck analysis and distributed debugging.

## Logging
4. **Structured JSON Logs**: All logs in production must be formatted as structured JSON. Never use plain text logs in production.
5. **Mandatory Fields**: Every log entry must include: `timestamp` (ISO8601), `level` (INFO, WARN, ERROR, DEBUG), `service_name`, `message`, and `trace_id` (if within a request context).
6. **Log Levels**:
   - `ERROR`: System is in distress, requires immediate attention (e.g., database down).
   - `WARN`: Unexpected behavior but system recovers (e.g., retrying an API call).
   - `INFO`: Significant lifecycle events (e.g., Service started, User registered).
   - `DEBUG`: Verbose information for troubleshooting (disabled in production).
7. **No PII**: Never log Personally Identifiable Information (PII), passwords, or secrets. Mask or redact sensitive fields before logging.

## Metrics
8. **RED Method**: For every service, monitor Rate (requests per second), Errors (error rate), and Duration (latency distribution).
9. **USE Method**: For infrastructure, monitor Utilization, Saturation, and Errors of resources (CPU, Memory, Disk, Network).
10. **Business Metrics**: Emit custom metrics for critical business events (e.g., `orders_placed_total`, `payment_failures_total`).

## Distributed Tracing
11. **OpenTelemetry**: Use OpenTelemetry standard for trace propagation.
12. **Context Propagation**: Every incoming HTTP request or message must extract the trace context (e.g., W3C `traceparent` header) and inject it into all outgoing HTTP requests and database calls.
13. **Span Granularity**: Create spans for significant operations: database queries, external API calls, and complex internal computations. Do not create a span for every function call.

## Alerting
14. **Actionable Alerts**: Only alert on symptoms that impact users (e.g., high latency, high 5xx rate), not causes (e.g., high CPU usage). If an alert fires, a human must be required to take action.
15. **Runbooks**: Every alert must include a link to a runbook detailing how to investigate and resolve the issue.
