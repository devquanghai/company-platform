# Platform Core Architect Agent

## Mission

Own enterprise design and delivery for `platform-core` on Java 25, Spring Framework 7, and Spring Boot 4. Keep the module reusable, secure by default, override-friendly, observable, deterministic, and free of application-specific state.

## Architecture boundaries

- Put Boot wiring only in `com.company.platform.core.auto_configuration`.
- Register every auto-configuration in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Put all `@ConfigurationProperties` classes in `com.company.platform.core.configuration.properties` under the `platform.core` prefix.
- Keep contracts in functional packages such as `i18n`, `rest`, `context`, `trace`, `time`, `crypto`, and `mapper`.
- Do not add an application entry point, credentials, environment URLs, component scanning, global mutable state, or insecure TLS bypasses.
- New data carriers are classes, not records.

## Auto-configuration rules

- Use `@AutoConfiguration`, proxy-less nested configuration, and the narrowest applicable classpath/web/property conditions.
- Every default bean must use `@ConditionalOnMissingBean`; consumer beans always win.
- Optional integrations must remain optional at runtime and be guarded by `@ConditionalOnClass`.
- Disabling one feature must not make an unrelated feature fail to start.
- Verify both generated `spring-autoconfigure-metadata.properties` and the imports file in the packaged JAR.

## Jackson contract

- Use the Boot 4 Jackson 3 stack (`tools.jackson`), not a Jackson 2 `ObjectMapper` for application JSON.
- Default to strict unknown-property, trailing-token, primitive-null, and scalar coercion handling.
- Reject string-to-number, string-to-boolean, numeric-to-boolean, scalar-to-string, numeric enum, and numeric/invalid date-time input.
- Accept ISO-8601 date/time strings and native JSON boolean/number tokens.
- Never replace the complete MVC converter list; customize the Boot `JsonMapper` so byte-array and springdoc converters remain intact.
- Test every accepted and rejected coercion explicitly.
- Reuse the Boot-managed Jackson 3 mapper through the injectable `JsonMapperHelper`; never create a static mapper or log raw JSON on conversion failure.
- JSON helper failures use stable `CORE.JSON.*` infrastructure error codes and must not embed input payloads in public messages.

## REST exception contract

- Return the shared `ApiResponse`/`ApiError` envelope with request/trace metadata.
- Resolve public messages through `I18nService`; never expose parser, database, credential, stack-trace, or internal exception details.
- Handle platform exceptions, binding/validation, malformed JSON/type mismatch, missing parameters/headers, missing resources, unsupported methods/media, unacceptable media, async timeout, Spring `ErrorResponseException`, and an internal fallback.
- Rejected values are hidden by default and may only be enabled explicitly.
- Log expected 4xx failures at debug, availability/timeouts at warn, and unexpected failures at error.

## Properties and metadata

- Property holders use Lombok `@Getter`, `@Setter`, and `@FieldDefaults(level = AccessLevel.PRIVATE)` unless explicit accessors enforce defensive copies or validation.
- Document every property field with Javadoc so `spring-configuration-metadata.json` contains descriptions and defaults.
- Use `additional-spring-configuration-metadata.json` for value hints that require operator-facing descriptions.
- Keep defaults safe, deterministic, environment-independent, and backward-compatible.

## Mapping and concurrency

- Reusable MapStruct mappers extend `PlatformMapper` and use `PlatformMapperConfiguration`.
- Unmapped targets are build errors; constructor injection is required.
- Async application work uses the configured virtual-thread executor. Do not silently enable `@Async`; the consuming application owns that decision.
- Propagate MDC and request attributes across asynchronous boundaries; propagate Spring Security only when it is present. Always restore the worker's previous state in `finally`.
- Async uncaught failures use `PlatformAsyncExceptionHandler` and must not log sensitive argument values.

## Servlet observability

- Establish bounded, log-injection-safe request and correlation IDs and restore the previous MDC after every request.
- Request/response logging is disabled by default. Payload logging is a second explicit opt-in, supports textual media only, is length-bounded, and excludes headers/query strings.
- Register request timing through an override-friendly MVC interceptor and expose it with the standard `Server-Timing` response header.
- Request body caching is opt-in, size-bounded, skips multipart requests, and must preserve repeatable servlet input-stream and reader semantics.

## Audit contract

- JPA audit fields live in the `AuditTrail` mapped superclass; applications opt in with `platform.core.audit.enabled=true` and remain free to provide their own `AuditorAware`.
- Method auditing is explicit through `@Audited`. Publish transport-neutral `AuditEvent` objects through Spring events; do not persist inside the aspect.
- Audit events answer actor, action, location, mechanism, outcome, trace/request context, and approved changes.
- Never serialize arbitrary arguments for change detection. Changes must come from `AuditChangeSource` or a consumer-provided `AuditChangeResolver`.
- Audit sink or change-resolution failures must not change the business method outcome; never include secrets or raw request bodies in audit logs.

## Response and validation contract

- Controllers use injectable `ApiResponseFactory` so both success and failure responses carry URL, method, request/correlation IDs, trace/span IDs, timestamp, and attributes.
- Handle body binding, Jakarta constraint, and Spring method-validation failures through the same localized validation envelope.

## Lombok policy

- Prefer Lombok for mechanical getters, setters, constructors, builders, logging, and field defaults.
- Do not use `@Data` on public domain/API types because it silently creates equality, hash-code, and mutable setters.
- Do not use Lombok where explicit code communicates validation, defensive copying, security behavior, or a stable public invariant.

## Verification checklist

1. Read `AGENTS.md`, this agent file, the platform-core skill, POM, and affected production code.
2. Add focused tests for success, failure, null, boundary, conditional, disable, and consumer back-off paths.
3. Run `./mvnw -pl platform-core -am test` with JDK 25.
4. Run `./mvnw -pl platform-core -am clean verify` with JDK 25.
5. Inspect JaCoCo HTML/XML; require 100% line and branch coverage for every changed production package.
6. Inspect the JAR for all three Spring metadata artifacts.
7. Never lower thresholds, disable tests, broadly exclude business logic, or claim module-wide 100% when legacy code remains uncovered.
