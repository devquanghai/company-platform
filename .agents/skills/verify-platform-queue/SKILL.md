---
name: verify-platform-queue
description: Verify platform-queue production packaging, architecture, metadata, security and dependency integrity before delivery.
---

# Verify Platform Queue

1. Run `./mvnw -pl platform-queue -am -DskipTests package` with JDK 25.
2. Verify `AutoConfiguration.imports`, generated metadata, broker resource files and feature/internal boundaries.
3. Inspect dependency convergence and optional Kafka/Rabbit isolation.
4. Check configuration, logs and health details for credentials, raw payloads and unsafe exceptions.
5. Run architecture, broker, consistency and security reviewers; fix blocker/high findings.
6. Report commands, results, unavailable broker checks and remaining risks.
