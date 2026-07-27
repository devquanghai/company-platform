# Platform Logging Discovery

## Repository baseline

- Parent/reactor: `com.company.platform:platform-parent:1.0.0-SNAPSHOT`.
- Java 25; Spring Boot 4.0.7; Spring Framework 7.0.8.
- Jackson 3.1.4, SLF4J 2.0.18, Logback 1.5.34.
- Micrometer 1.16.6, Tracing 1.6.6, Lombok 1.18.46.
- JUnit 5, AssertJ, `ApplicationContextRunner`, Surefire/Failsafe 3.5.5
  and JaCoCo 0.8.14 are inherited from the root parent.
- There is no separate physical `platform-parent` or
  `platform-dependencies` module; the root POM performs both roles.

## Reusable abstractions

- `platform-core` provides `CurrentUserProvider`, `RequestContextProvider`,
  `TraceContextProvider`, `TimeProvider` and `JsonMapperHelper`.
- `TraceContextFilter` validates request/correlation IDs and restores MDC.
- `ContextCopyingTaskDecorator` propagates/restores MDC for async work.
- Spring event publishing and fail-open audit patterns exist in core.

## Existing boundaries and risks

- No reusable masking contract exists. The service-exchange masker is local and
  must not create a reverse dependency.
- Core crypto utilities include legacy compatibility behavior and log failures;
  they are unsuitable for a strict versioned ciphertext envelope.
- No top-level Logback config is packaged. This library must ship include
  fragments only.
- Servlet, WebFlux, Micrometer and Jasypt remain optional integrations. Jasypt
  has no repository-managed version.
- Core request logging currently performs bounded control-character cleanup but
  no PII masking. Platform-logging startup validation rejects
  `platform.core.web.request-logging-enabled=true` together with raw payload
  inclusion while secure platform masking is active; a second request logger is
  never installed.

## Integration decisions

- Depend on `platform-core` and reuse its context, trace, time and JSON contracts.
- Isolate Logback types under `logback`; public APIs are SLF4J-neutral.
- Make masking mandatory at structured logging entry points; converters are
  defense in depth.
- Implement strict JCA AES-GCM and RSA-OAEP/hybrid strategies without legacy
  fallback. Keep keys external through `KeyProvider`.
- Treat unknown/non-local environments as secure. Production policy is derived
  from active Spring profiles as well as the configured environment and cannot
  be relaxed by changing only `platform.logging.environment`.
- Register features through ordered Boot auto-configurations with bean back-off
  and no component scanning.
