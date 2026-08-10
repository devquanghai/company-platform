---
name: verify-platform-cache
description: Verify platform-cache production packaging, architecture, metadata, security and dependency integrity before delivery.
---

1. Run `./mvnw -pl platform-cache -am -DskipTests package` with JDK 25.
2. Verify dependency convergence, optional-provider isolation and managed versions.
3. Inspect packaged Lua/resources, `AutoConfiguration.imports`, and generated metadata.
4. Check package-by-feature/internal boundaries and public API leakage.
5. Search packaged resources and configuration for credentials, unsafe defaults and raw sensitive values.
6. Report commands, results, unavailable external topology checks and remaining risks.
