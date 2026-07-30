---
name: build-platform-queue
description: Build or extend platform-queue with named Kafka and RabbitMQ brokers, retries, dead letters, outbox, inbox, observability, security, and Spring Boot auto-configuration. Use for implementation or refactoring under platform-queue.
---

# Build Platform Queue

1. Read the root `AGENTS.md` and `platform-queue/docs/platform-queue-discovery.md`.
2. Inspect the root POM and reusable platform-core, platform-logging, cache, and exchange conventions.
3. Run the read-only `queue_explorer` before changing production code.
4. Reuse platform time, JSON, context, tracing, masking, exception, and event abstractions.
5. Keep provider-neutral contracts separate from Kafka- and Rabbit-specific extensions.
6. Preserve Kafka partition/offset/transaction semantics and Rabbit exchange/queue/acknowledgement semantics.
7. State at-least-once guarantees; never claim database-plus-broker exactly-once.
8. Forbid automatic cross-broker fallback and in-memory production durability.
9. Implement one vertical slice at a time and compile after each material slice.
10. Add unit tests and real-broker integration tests for changed delivery behavior.
11. Run all queue review agents and fix blocker/high findings.
12. Finish with `$verify-platform-queue`.
