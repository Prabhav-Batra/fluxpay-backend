# Scenario: Migrate from REST to Event-Driven

**Domain**: Social Media
**Difficulty**: High Risk (Architecture Rewrite)

## Description
The `feed-service` currently pulls data synchronously from the `user-service` via REST, causing cascading failures. We must migrate this to an asynchronous event-driven model using Kafka.

## Expected Workflow
- `workflows/refactor.md`
- Needs `reviews/architecture.md`, `reviews/performance.md`

## Exit Criteria
- Synchronous HTTP calls are removed.
- `feed-service` builds localized read-models based on Kafka events.
