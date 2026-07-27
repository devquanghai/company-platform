---
name: build-platform-logging
description: Build or extend platform-logging auto-configuration, structured logging, masking, annotations, context propagation, Logback integration and crypto extensions.
---

1. Read root `AGENTS.md`, the parent POM and `platform-core` contracts.
2. Run or reproduce logging discovery before changing source.
3. Reuse request, trace, user, time and JSON abstractions.
4. Keep SLF4J public and isolate Logback types under `logback`.
5. Keep masking and crypto independent; logging must never decrypt.
6. Make mandatory credential masking impossible to weaken.
7. Use authenticated/versioned crypto with external versioned keys.
8. Implement Boot auto-configuration without component scanning; back off for
   every consumer-provided contract.
9. Add behavioral/security tests and run architecture/security review.
10. Run `./mvnw -pl platform-logging -am clean verify` and inspect JaCoCo.
