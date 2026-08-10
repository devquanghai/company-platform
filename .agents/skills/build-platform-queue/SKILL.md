---
name: build-platform-queue
description: Build or extend platform-queue with named Kafka and RabbitMQ brokers, retries, dead letters, outbox, inbox, observability, security, and Spring Boot auto-configuration. Use for implementation or refactoring under platform-queue.
---

# Build Platform Queue

1. Read `AGENTS.md`, queue discovery docs and shared platform contracts; run `queue_explorer` first.
2. Build publish, consume, topology and reliability as feature slices with internal ports/adapters; preserve supported API FQCN.
3. Keep provider-neutral contracts separate from Kafka/Rabbit extensions and preserve native delivery semantics.
4. State at-least-once guarantees; forbid automatic cross-broker fallback and in-memory production durability.
5. Compile after each vertical slice. Run queue architecture, broker, consistency and security reviewers.
6. Finish with `$verify-platform-queue`.
