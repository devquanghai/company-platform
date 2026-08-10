---
name: verify-platform-logging
description: Verify platform-logging production packaging, architecture, metadata, dependency and security integrity before delivery.
---

1. Run `./mvnw -pl platform-logging -am -DskipTests package` with JDK 25.
2. Validate dependency convergence, optional integrations and feature/internal boundaries.
3. Inspect `AutoConfiguration.imports`, generated metadata and packaged resources.
4. Verify no top-level Logback configuration, plaintext secrets or unsafe crypto defaults are packaged.
5. Run logging architecture and security reviewers; fix blocker/high findings.
6. Report commands, results and remaining risks.
