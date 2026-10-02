# Global Standard: Messaging & Event-Driven Architecture

## Event Design
1. **Event definition**: An event represents something that *has already happened*. Name events in the past tense (e.g., `OrderPlaced`, `UserRegistered`).
2. **Payload contents**: Events should contain enough context for consumers to process them without immediately querying the producer (Event-Carried State Transfer), but must not contain sensitive PII unless strictly necessary and encrypted.
3. **Schema Registry**: All event schemas (JSON Schema, Avro, Protobuf) must be versioned and managed in a central schema registry to ensure compatibility between producers and consumers.
4. **Idempotency keys**: Every event must include a unique `eventId` or `idempotencyKey` to allow consumers to detect and handle duplicate deliveries.

## Producers
5. **Outbox Pattern**: When a business transaction requires updating a database and publishing an event, use the Transactional Outbox pattern to guarantee atomicity and prevent lost events.
6. **Fire-and-forget vs. Confirmed**: Producers must await acknowledgment from the message broker before considering an event successfully published.
7. **Metadata headers**: Include routing and tracing metadata (e.g., `correlationId`, `causationId`, `timestamp`, `sourceService`) in message headers/attributes, not in the main payload.

## Consumers
8. **Idempotent processing**: Consumers must be mathematically idempotent. Message brokers guarantee "at least once" delivery, so consumers *will* receive duplicate messages eventually.
9. **Dead Letter Queues (DLQ)**: Consumers must configure a DLQ for messages that fail processing after a defined number of retries. Alert on DLQ depth.
10. **Consumer groups**: Use consumer groups to allow horizontal scaling of message processing. Ensure partitioning keys (e.g., `userId`) are used to maintain ordering for related events.
11. **Poison pill handling**: Consumers must catch deserialization errors and route malformed messages to a DLQ immediately to prevent blocking the partition.

## Topology & Infrastructure
12. **Topics vs Queues**: Use topics (Pub/Sub) when multiple independent services need to react to the same event. Use queues (Point-to-Point) for load-balanced task distribution.
13. **Ordering guarantees**: Assume messages arrive out of order unless explicitly using partitioned topics (like Kafka partitions or AWS SQS FIFO) grouped by a specific key.
14. **Retention**: Configure appropriate retention policies on topics. Events are transient; they are not a substitute for a long-term database of record unless utilizing Event Sourcing.
