---
name: verify-platform-queue
description: Run the complete platform-queue compile, unit, integration, metadata, security, dependency, and JaCoCo quality gate. Use before delivering platform-queue changes.
---

# Verify Platform Queue

1. Compile `platform-queue` and required reactor modules with JDK 25.
2. Run unit and auto-configuration tests.
3. Run Kafka and RabbitMQ Testcontainers integration tests when Docker is available.
4. Run inbox/outbox concurrency and failure-recovery tests.
5. Verify auto-configuration imports and generated configuration metadata.
6. Inspect dependency convergence and optional broker dependency behavior.
7. Inspect logs and health details for credentials, raw payloads, and unsafe exceptions.
8. Run JaCoCo and require at least 85% line and 80% branch coverage.
9. Run `queue_test_reviewer` and fix blocker/high findings.
10. Run `./mvnw -pl platform-queue -am clean verify`.
11. Report commands, results, coverage, skipped environmental tests, and limitations.
