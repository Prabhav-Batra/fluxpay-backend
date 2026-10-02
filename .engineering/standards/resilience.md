# Global Standard: Resilience

## Philosophy
1. **Assume Failure**: Network calls will fail, databases will go down, disks will fill up, and instances will crash. Design every component with the assumption that its dependencies will fail.
2. **Graceful Degradation**: If a non-critical dependency fails, the system must continue to operate with reduced functionality, rather than failing completely.

## Patterns
3. **Timeouts**: Every external network call (HTTP, gRPC, database, cache) must have a strict, explicit timeout. Never use infinite timeouts. Set timeouts based on the 99th percentile expected latency.
4. **Retries with Exponential Backoff**: Implement retries for transient failures (e.g., HTTP 503, network resets). Use exponential backoff (e.g., 1s, 2s, 4s) to prevent overwhelming a recovering downstream service.
5. **Jitter**: Always add randomness (jitter) to retry intervals to prevent Thundering Herd problems when many clients retry simultaneously.
6. **Circuit Breakers**: Wrap calls to external services in a Circuit Breaker. If the error rate exceeds a threshold, trip the breaker and fail fast to prevent cascading failures and give the downstream service time to recover.
7. **Bulkheads**: Isolate resources (e.g., thread pools, connection pools) used for different dependencies. A slow or failing dependency must not consume all resources and starve other parts of the system.

## Capacity & Scaling
8. **Rate Limiting**: Protect your own services by implementing rate limiting (e.g., Token Bucket, Sliding Window) to shed excess load before it causes the system to crash.
9. **Load Shedding**: When the system is overloaded (e.g., high CPU, high queue depth), actively reject new requests (return HTTP 503) to ensure existing requests can complete successfully.

## State Management
10. **Stateless Services**: Design application servers to be completely stateless. Any server should be able to handle any request, allowing for seamless horizontal scaling and easy replacement of failed nodes.
11. **Idempotency**: Ensure that operations can be safely retried without unintended side effects. Critical for distributed systems where network partitions can hide whether an operation succeeded or failed.
