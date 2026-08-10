---
name: platform-core-development
description: Develop and review the Java 25/Spring Boot 4 platform-core module, especially feature boundaries, auto-configuration, shared APIs, i18n and crypto. Use for changes under platform-core or reusable platform conventions.
---

# Platform Core Development

1. Read `AGENTS.md`, `platform-core/pom.xml`, affected code, and the architecture reference before structural changes.
2. Preserve public FQCN and behavior unless the request explicitly permits a breaking change.
3. Organize by feature; keep implementation under each feature's `internal` ports/adapters and Boot wiring at the composition boundary.
4. Register auto-configuration in `AutoConfiguration.imports`; guard optional integrations and back off for consumer beans.
5. Keep feature properties under `platform.core.*`, document fields, and generate both Spring metadata artifacts.
6. Keep Jackson 3 coercion strict; inject the Boot mapper and return stable, localized, non-sensitive errors.
7. Propagate and restore MDC/request/security context. Keep trace IDs and opt-in payload caching/logging bounded.
8. Keep audit opt-in, transport-neutral, non-sensitive, and unable to change business outcomes.
9. Run `./mvnw -pl platform-core -am -DskipTests package` with JDK 25; inspect packaged metadata and resources.

Read [references/architecture.md](references/architecture.md) when changing auto-configuration boundaries or public packages.
