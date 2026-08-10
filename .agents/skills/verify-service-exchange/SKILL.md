---
name: verify-service-exchange
description: Verify platform-service-exchange production packaging, architecture, metadata, security and dependency integrity after changes.
---

1. Run `./mvnw -pl platform-service-exchange -am -DskipTests package` with JDK 25.
2. Inspect dependency convergence, managed versions and optional transport boundaries.
3. Check packaged auto-configuration imports, generated metadata and resources.
4. Check feature/internal boundaries, public vendor leakage and HTTP/gRPC lifecycle ownership.
5. Run architecture and security reviewers; fix blocker/high findings.
6. Return commands, results, remaining risks and changed files.
