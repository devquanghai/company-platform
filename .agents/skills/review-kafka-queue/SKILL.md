---
name: review-kafka-queue
description: Review platform-queue Kafka producer, consumer, ordering, retry topics, DLT, topology, and transaction behavior. Use for Kafka adapter and configuration changes.
---

# Review Kafka Queue

1. Verify `acks=all`, producer idempotence, safe in-flight requests, and bounded retries.
2. Verify message keys preserve required per-partition ordering.
3. Verify consumer groups, ack mode, offset commits, rebalance, and shutdown behavior.
4. Verify retry-topic naming, attempts, backoff, original metadata, and terminal DLT.
5. Verify fatal deserialization/schema/security failures do not retry indefinitely.
6. Verify transactional ID uniqueness and `read_committed` behavior.
7. Verify Kafka transactions cover Kafka read/process/write only.
8. Verify named clusters own and close only resources created by the module.
9. Verify topology validation is non-destructive.
10. Require Testcontainers Kafka coverage for changed broker behavior.
