---
name: build-platform-logging
description: Build or extend platform-logging auto-configuration, structured logging, masking, annotations, context propagation, Logback integration and crypto extensions.
---

1. Read `AGENTS.md`, parent POM and platform-core contracts; reproduce logging discovery first.
2. Build logging, masking, crypto and audit as feature slices with internal ports/adapters; preserve supported API FQCN.
3. Reuse request, trace, user, time and JSON abstractions. Keep SLF4J public and Logback internal.
4. Keep masking and crypto independent; logging never decrypts and credential masking cannot weaken.
5. Use authenticated, versioned crypto with external versioned keys.
6. Run logging architecture/security reviewers, then `$verify-platform-logging`.
