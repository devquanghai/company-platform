# Platform Logging

Enterprise library for structured SLF4J logging, PII masking, MDC propagation,
audit/metrics and strict versioned cryptography. It contains no business logic
and does not install an application entry point or root Logback configuration.

## 1. Goal and architecture

The artifact keeps masking, crypto, Logback adapters and Boot
auto-configuration in separate packages. SLF4J remains the logging API and
Micrometer Observation/Tracing remains the observability API; the module does
not replace it.
Logging never decrypts data.
See [design](../docs/platform-logging-design.md).

## 2. Package structure

- `api`, `annotation`, `domain`: public backend-neutral contracts.
- `masking`, `crypto`, `structured`: platform policy implementations.
- `logback`: the only package coupled to Logback.
- `audit`, `observability`: aggregate events and low-cardinality metrics.
- `autoconfigure`: bean wiring only; no component scan.

## 3. Maven dependency

```xml
<dependency>
  <groupId>com.company.platform</groupId>
  <artifactId>platform-logging</artifactId>
</dependency>
```

Versions are inherited from `platform-parent`.

## 4. Auto-configuration

Auto-configurations are registered through `AutoConfiguration.imports`.
Các thành phần nền tảng được kích hoạt theo classpath và bean cần thiết. Không
có cờ `platform.logging.enabled`: thêm starter vào ứng dụng đồng nghĩa baseline
masking luôn hoạt động. Chỉ những khả năng thật sự tùy chọn mới có property bật,
ví dụ Jasypt hoặc phát Spring audit event. Consumer beans for every public
contract win.

## 5. Lombok `@Slf4j`

Application code uses Lombok `@Slf4j`; SLF4J 2 fluent key-values are the
standard structured logging API. The internal `PlatformLogger` compatibility
contract exists only for the annotation aspect and delegates to SLF4J after
applying masking/audit policy.

## 6. Structured logging

```java
@Slf4j
@Service
public class CustomerService {
    public void update(String id, String maskedEmail) {
        log.atInfo()
            .addKeyValue("event.name", "CUSTOMER_UPDATED")
            .addKeyValue("customer.id", id)
            .addKeyValue("customer.email", maskedEmail)
            .log("Customer update completed");
    }
}
```

Sensitive values must pass through `DataMaskingService` before reaching SLF4J.
The platform annotation aspect performs this step automatically for annotated
method events.

## 7. Text and JSON logs

TEXT uses the supplied masking pattern converters. ECS, GELF and LOGSTASH use
Spring Boot 4 `StructuredLogEncoder`; no custom JSON encoder is maintained.
The JSON and audit fragments install a fail-closed filter that drops any direct
SLF4J event still containing mandatory secrets in its message, MDC, key-values
or throwable. Prefer SLF4J with already-sanitized values, or the platform
annotations when the event needs automatic field-level masking.

## 8. Logback fragments

The application owns `logback-spring.xml`, because Logback starts before Spring
auto-configuration. For text output, include the complete fragment:

```xml
<configuration>
  <include resource="com/company/platform/logging/logback/platform-console.xml"/>
</configuration>
```

The library intentionally does not package a top-level `logback-spring.xml`;
see `docs/examples/logback-spring.xml` for profile-based text/JSON output.

## 9. Environment profiles

Use TEXT console in local/dev/test. Use ECS/GELF/LOGSTASH stdout in
staging/production. File logging is opt-in to avoid duplicate container
ingestion.

## 10. PII masking

The pipeline applies mandatory rules, `@Sensitive`, JSON path, exact
field/header/query/MDC rules, regex fallback, control sanitization and bounds.
Source objects are never mutated.

## 11. Masking types

`FULL`, `PARTIAL`, `SUBSTITUTION`, `HASH` and `REMOVE` are supported.
`REMOVE` omits a structured member. HASH should use an external HMAC key in
secure environments.

## 12. Annotation masking

Use `@Sensitive`, `@MaskEmail`, `@MaskPhone`, `@MaskCardNumber`,
`@MaskAccountNumber`, `@MaskToken` or `@MaskPassword` on fields, record
components and parameters.

## 13. Configuration-based masking

Rules support FIELD_NAME, JSON_PATH (`$`, property, index and `[*]` subset),
REGEX, HEADER, QUERY_PARAMETER and MDC_KEY. Invalid/duplicate/unsafe rules fail
startup.

## 14. Custom masking bean

Implement `MaskingStrategy` and reference its bean name from a rule or
`@Sensitive(strategyBean=...)`. Secure mandatory rules cannot be weakened.

## 15. Method logging

`@Loggable` logs completion/failure and monotonic duration. Arguments/results
are false by default. `@NoLogging` wins; method metadata overrides type metadata.

## 16. Spring proxy limitations

Method logging and crypto annotations apply to Spring-proxied public methods.
Self-invocation, private methods and final methods are not intercepted.

## 17. Trace and context

Trace/span context comes from the platform `TraceContextProvider`, backed by
Spring Boot Micrometer Observation/Tracing when available. The library does not
depend directly on OpenTelemetry and does not maintain a second tracing or MDC
lifecycle.

## 18. Servlet

The module reuses platform-core `TraceContextFilter`; it does not install a
second request-ID owner. Enabling core raw payload logging together with this
module fails startup because it would bypass masking.

## 19. WebFlux

WebFlux propagation is owned by Reactor Context together with Spring Boot
Micrometer Observation instrumentation. No custom WebFilter is installed.

## 20. Async and virtual threads

Spring Boot/Micrometer Context Propagation owns trace propagation. Existing
`platform-core` request/security propagation remains available for application
executors; `platform-logging` does not install another task decorator.

## 21. Encryption and decryption

Prefer explicit `CryptoService` for critical flows. Callers select a provider,
allowlisted algorithm and external key alias; arbitrary JCA transformations are
not accepted.

## 22. AES-GCM

`AES_GCM_256` requires a 256-bit AES key, fresh 96-bit nonce and 128-bit tag.
Envelope metadata is authenticated as AAD. Tampering fails closed.

## 23. RSA-OAEP

`RSA_OAEP_SHA256` requires RSA keys of at least 2048 bits and explicit
SHA-256/MGF1-SHA256 OAEP parameters. PKCS#1 v1.5 is never a fallback.

## 24. Hybrid encryption

Payload above the OAEP direct bound uses a random AES-256-GCM data key; RSA-OAEP
wraps that key. Temporary data-key bytes are cleared in `finally`.

## 25. Jasypt compatibility

Jasypt is an extension-only compatibility provider, disabled by default. A
consumer must add and manage a compatible Jasypt dependency, enable the
provider and supply its password through `KeyProvider`. No fallback occurs.

## 26. Key provider and cache

Applications must provide `KeyProvider` (Vault/KMS/HSM/custom). The default
provider rejects operations. `CachingKeyProvider` is bounded, TTL-based,
clearable and never caches plaintext/decrypted values.

## 27. Key rotation

Each envelope contains a non-secret key version. Encryption uses the active
version; decryption resolves the recorded version. `CryptoRotationService`
detects and re-encrypts old envelopes.

## 28. Custom crypto strategy/provider

Register `CryptoStrategy` or `CryptoProviderFactory`. Resolution uses provider +
algorithm or an explicit trusted bean name. A custom strategy never disables
JCA secure defaults.

## 29. Crypto annotations

`@EncryptValue`, `@DecryptValue`, `@EncryptResult`, `@DecryptResult` support
String and `byte[]`. Crypto-annotated values are always excluded from method
logs. Field access is not intercepted; use an explicit `CryptoObjectProcessor`.

## 30. Audit and security logging

Aggregate Spring events contain operation/outcome/provider/algorithm/counts and
trace/request IDs only—never plaintext, key, full ciphertext or payload.
FAIL_OPEN is default; FAIL_CLOSED is explicit for compliance flows.

## 31. Metrics

Micrometer integration records `platform.logging.*` and `platform.crypto.*`
meters with low-cardinality tags only. User/request/trace IDs and key aliases
are never tags.

## 32. Security warnings

Production rejects disabled mandatory masking, weak/legacy crypto, inline keys,
crypto annotations without crypto, missing Jasypt classes and raw core request
payload logging. Unknown environments are treated as secure.

## 33. Performance

Rules/regexes compile once, expensive serialization is skipped below log level,
and depth/string/map/collection/envelope sizes are bounded. Cipher instances are
per operation and registries are thread-safe.

## 34. Troubleshooting and migration

- Missing key provider: register an external `KeyProvider`.
- Invalid envelope: verify provider/algorithm/alias/version and AAD.
- Duplicate request logs: disable `platform.core.web.request-logging-enabled`.
- Legacy core ciphertext is intentionally not auto-decrypted. Migrate through an
  explicit offline adapter, then store the new `ENC[v1:...]` format.
