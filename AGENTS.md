# Repository agent rules

## platform-core

- Use `.agents/skills/platform-core-development/SKILL.md` for changes in `platform-core/**`.
- Keep the module a reusable library; do not add application entry points or runtime environment credentials.
- Register Boot auto-configuration through `AutoConfiguration.imports`; do not rely on component scanning.
- Every default bean must back off when the consuming application supplies an equivalent bean.
- New and changed production code requires unit tests with 100% line and branch coverage.
- Keep all platform properties in `com.company.platform.core.configuration.properties`; generate both Spring configuration and auto-configuration metadata.
- Use Lombok for mechanical boilerplate, but avoid `@Data` and keep invariant/security logic explicit.
- Keep Jackson 3 coercion strict and exception responses localized, stable, and non-sensitive.
- Do not hide uncovered production behavior with broad JaCoCo exclusions.
- Use JDK 25 and the repository Maven Wrapper for validation.
- Treat insecure TLS bypass, weak encryption, logging secrets, dynamic dependency versions, and legacy external package imports as build blockers.

## Repository architecture

- Shared platform modules must not contain business-domain logic.
- Keep public API separate from transport implementation and prefer composition.
- Activate library integrations with Boot auto-configuration, never component scanning.
- Do not expose vendor-specific HTTP types; expose gRPC types only where generated stubs require them.
- Manage dependency versions in the platform parent/BOM, not child modules.
- Reuse existing platform abstractions before introducing a new dependency or duplicate helper.
- Never log credentials, tokens, cookies, private keys, proxy passwords, or unmasked PII.
- Insecure SSL is disabled by default; POST and PATCH are not retried unless explicitly idempotent.

## platform-service-exchange

- Use `.agents/skills/build-service-exchange/SKILL.md` for implementation work.
- Keep REST, gRPC, resilience, fallback, audit, and observability in one artifact with separate packages.
- A named-client registry owns only resources it creates; Spring-created gRPC channels remain Spring-owned.
- Run `./mvnw -pl platform-service-exchange -am clean verify` before completion.

## platform-logging

- Use `.agents/skills/build-platform-logging/SKILL.md` for implementation work.
- Keep logging, masking, crypto and auto-configuration in one artifact with
  strict package boundaries; logging must never decrypt.
- Public APIs must remain SLF4J-neutral and must not expose Logback types.
- Treat raw PII/credentials, log injection, weak crypto, IV reuse, inline keys,
  unversioned ciphertext and MDC leaks as build blockers.
- Ship reusable Logback fragments only; never install a top-level
  `logback-spring.xml` from the library.
- Run `./mvnw -pl platform-logging -am clean verify` and require at least 85%
  line and 80% branch coverage.
