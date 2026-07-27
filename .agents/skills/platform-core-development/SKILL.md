---
name: platform-core-development
description: Develop and review the Java 25/Spring Boot 4 platform-core module, especially auto-configuration, shared APIs, i18n, crypto, tests, and JaCoCo gates. Use for any change under platform-core or when adding reusable platform conventions.
---

# Platform Core Development

1. Read `AGENTS.md`, `platform-core/pom.xml`, and affected production classes before editing.
2. Preserve public API compatibility unless the request explicitly permits a breaking change.
3. Keep Spring Boot integration in `auto_configuration`; register it in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
4. Keep every properties holder in `configuration.properties`, use the `platform.core` prefix, Lombok accessors/field defaults, field Javadoc, and generated Spring metadata.
5. Guard optional integrations with Boot conditions. Make every user-provided bean win with `@ConditionalOnMissingBean`.
6. Keep Jackson 3 scalar coercion strict by default and test native/invalid boolean, numeric, enum, string, null, and ISO date/time tokens.
7. Keep public exception responses i18n-based and non-sensitive; cover standard Spring MVC error categories plus an internal fallback.
8. Keep secrets and environment-specific values out of defaults. Never auto-configure insecure TLS bypasses.
9. Add focused unit tests for success, failure, null, boundary, conditional, and back-off paths.
10. Run `./mvnw -pl platform-core -am clean verify` with JDK 25. Read the JaCoCo XML/HTML report; never achieve coverage by excluding business logic.
11. Require 100% line and branch coverage for changed production packages. If untouched legacy code prevents a module-wide gate, report the exact uncovered classes and do not claim 100%.
12. Verify `AutoConfiguration.imports`, generated autoconfigure metadata, generated configuration metadata, and an `ApplicationContextRunner` test.
13. JSON convenience APIs must inject the Boot-managed Jackson 3 mapper, wrap failures with stable non-sensitive codes, and never maintain a static mapper.
14. Virtual-thread decorators must propagate and restore MDC/request/security context. Servlet trace IDs must be bounded and payload logging must remain explicit, textual-only, and bounded.
15. Request body caching is opt-in, size-bounded, repeatable, and must skip multipart input. Success responses use `ApiResponseFactory` to include current request and trace metadata.
16. Audit support is opt-in and conditional on its optional integrations. Use explicit `@Audited` operations, safe `AuditChangeSource`/`AuditChangeResolver` data, and Spring events; an audit failure must not alter business behavior.

Read [references/architecture.md](references/architecture.md) when changing auto-configuration boundaries or public packages.
