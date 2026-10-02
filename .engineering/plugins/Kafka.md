# Kafka Plugin

## Registration
- ID: `projectos.plugins.kafka`
- Version: `1.0.0`
- Compatibility: `Apache Kafka 3.x`

## Capabilities
- **Standards**: Injects Kafka-specific messaging patterns, topic design, and consumer group rules.
- **Workflows**: Hooks into `infrastructure-change` for topic provisioning.

## Core Rules
1. **Partitioning**: Choose partition keys carefully. Messages with the same key are guaranteed to go to the same partition, preserving order.
2. **Exactly-Once Semantics (EOS)**: Use Kafka Transactions (`isolation.level=read_committed`) if exactly-once processing is required. Otherwise, design consumers to be idempotent (At-Least-Once).
3. **Consumer Groups**: Use consumer groups to scale consumption. Ensure the number of consumers in a group does not exceed the number of partitions, otherwise, extra consumers will sit idle.
4. **Schema Evolution**: Always use a Schema Registry (e.g., Confluent Schema Registry with Avro or Protobuf) to enforce forward/backward compatibility.

## Common Anti-Patterns
- ❌ Infinite Retries without Backoff: A poison pill message will block the partition forever if the consumer retries infinitely without a Dead Letter Queue (DLQ).
- ❌ Too Many Partitions: While partitions allow parallelism, having tens of thousands of partitions across a small cluster will overwhelm ZooKeeper/KRaft and increase latency.
- ❌ Relying on Kafka as a Long-Term Database: While Kafka has log retention, it is fundamentally a log, not a queryable database. Avoid infinite retention unless using specific features like compacted topics.

## Dependencies
- Requires: Apache Kafka cluster.
