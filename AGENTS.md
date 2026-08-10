# Repository agent rules

## platform-core

- Use `.agents/skills/platform-core-development/SKILL.md` for changes in `platform-core/**`.
- Keep the module a reusable library; do not add application entry points or runtime environment credentials.
- Register Boot auto-configuration through `AutoConfiguration.imports`; do not rely on component scanning.
- Every default bean must back off when the consuming application supplies an equivalent bean.
- Keep feature properties beside their feature under `internal/configuration/properties`; generate Spring configuration and auto-configuration metadata.
- Use Lombok for mechanical boilerplate, but avoid `@Data` and keep invariant/security logic explicit.
- Keep Jackson 3 coercion strict and exception responses localized, stable, and non-sensitive.
- Use JDK 25 and the repository Maven Wrapper for validation.
- Treat insecure TLS bypass, weak encryption, logging secrets, dynamic dependency versions, and legacy external package imports as build blockers.

## Repository architecture

- Treat each Maven module as a feature boundary. Inside a module, organize code by capability, never by a module-wide technical layer.
- Keep supported consumer contracts in `<feature>/api`.
- Keep implementation in `<feature>/internal/{domain,application,port/in,port/out,adapter}`. Boot wiring belongs in `<feature>/internal/autoconfigure`; a shared `internal/autoconfigure` is allowed only as module composition root.
- Dependencies point inward: adapters implement ports; application/domain do not import Spring or vendor APIs; cross-feature calls use `api`, never `internal`.
- Create ports only at real I/O or extension boundaries. Do not wrap every class with a one-to-one interface.
- Keep internal classes package-private unless framework wiring requires public visibility. A public type inside `internal` is not supported API.
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
- Keep REST, gRPC, resilience, fallback, audit, and observability as separate feature slices in one artifact; each slice owns its internal ports and adapters.
- A named-client registry owns only resources it creates; Spring-created gRPC channels remain Spring-owned.
- Run `./mvnw -pl platform-service-exchange -am -DskipTests package` before completion.

## platform-logging

- Use `.agents/skills/build-platform-logging/SKILL.md` for implementation work.
- Keep logging, masking, crypto and auto-configuration in one artifact with
  strict package boundaries; logging must never decrypt.
- Public APIs must remain SLF4J-neutral and must not expose Logback types.
- Treat raw PII/credentials, log injection, weak crypto, IV reuse, inline keys,
  unversioned ciphertext and MDC leaks as build blockers.
- Ship reusable Logback fragments only; never install a top-level
  `logback-spring.xml` from the library.
- Run `./mvnw -pl platform-logging -am -DskipTests package` before completion.
