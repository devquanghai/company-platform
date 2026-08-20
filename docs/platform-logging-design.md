# Platform Logging Design

## Boundaries

```text
api/annotation/domain
        |
        +--> masking --> structured logger --> audit/metrics
        +--> crypto --> key/envelope/provider
        +--> context
        +--> autoconfigure

logback --> masking defense-in-depth
```

Logging and crypto are independent; logging never decrypts. Public contracts
contain no Logback or business-domain types. Defaults are final, thread-safe and
replaceable. Properties live under `autoconfigure.properties`.

The root reactor adds `platform-logging` as a module and managed artifact.
The child contains no hard-coded dependency version. Jasypt starter, property
crypto and `ENC(...)` Environment integration are centrally owned by
`platform-core`; logging keeps only a deprecated API compatibility facade and
its opt-in payload PBE provider. The compatible version is pinned in root
dependency management.

## Masking pipeline

```text
annotation metadata
  -> mandatory field rules
  -> JSON path / field / header / query / MDC rules
  -> configured regex fallback
  -> control-character sanitization
  -> bounded depth, collection and string output
```

Identity-based cycle detection prevents recursion. Sanitization returns detached
immutable maps/lists/strings; `REMOVE` omits a field. Lower-priority rules cannot
weaken mandatory credential masking.

`MaskingStrategy` returns a typed `MaskingResult` (`MASKED`, `REMOVED`,
`UNCHANGED`) so removal is never encoded as `null` or a magic string. The JSON
path engine implements a documented, startup-compiled subset: root `$`, object
properties, array indices and `[*]`; unsupported syntax fails validation.

## Structured logging and annotations

`DefaultPlatformLogger` sanitizes fields, enriches core request/trace/user
context, applies customizers, sanitizes again and emits through SLF4J fluent API.
One aspect resolves method-over-type `@Loggable`; `@NoLogging` wins. Duration
uses `System.nanoTime()`. Arguments/results are processed only when explicitly
enabled and the target level is active.

Crypto-annotated arguments and results are always excluded from method logging,
independent of aspect order. The crypto guard aspect remains installed even
when crypto implementation is disabled; it fails at invocation before the
target method rather than silently returning plaintext. Implementation and
bridged/interface methods are resolved consistently. Field annotations are
handled only by an explicit object processor.

## Crypto

```text
CryptoService
  -> CryptoStrategyResolver (Factory Method)
      -> CryptoProviderFactoryRegistry (Abstract Factory)
          -> JCA AES-GCM / RSA-OAEP-hybrid strategies
  -> KeyProvider
  -> versioned CipherEnvelopeCodec
```

AES-GCM uses a fresh 96-bit nonce. The envelope has strict bounded fields:
format version, provider, algorithm, key alias/version, mode, nonce, wrapped
data key (hybrid only), ciphertext and authentication tag. Canonical metadata
`formatVersion/provider/algorithm/keyAlias/keyVersion` is authenticated as AAD;
hybrid encryption binds the same header to the content cipher. The parser
rejects missing/duplicate/unknown fields, control characters, unsupported
versions and oversized/invalid Base64 before key lookup.

RSA requires keys of at least 2048 bits and initializes OAEP explicitly with
SHA-256 and MGF1-SHA256. It enforces the direct input bound
`modulusBytes - 2 * sha256Bytes - 2`; larger input uses AES-GCM with a random
data key wrapped by RSA-OAEP. Temporary data-key/plaintext buffers are cleared
in `finally` where practical. Decryption resolves exact key purpose/version and
rejects malformed/tampered input without exposing provider messages. Jasypt is
an optional compatibility provider activated only when the consumer supplies
its classpath and explicitly enables it.

## Context, Logback and auto-configuration

Nested MDC scopes snapshot and restore prior state. By default the module reuses
core `TraceContextFilter` and `ContextCopyingTaskDecorator`; it does not create a
second request lifecycle owner. Servlet auto-configuration only enriches the
existing context and is conditional on a servlet web application. Reactive
support is an extension point unless Reactor is deliberately present.
Startup fails if core raw request/response payload logging would bypass masking.
The jar includes reusable Logback fragments only. JSON delegates to Spring Boot
4 `StructuredLogEncoder`.

`PlatformLogger` is the primary masking boundary and supports all application
rules. Logback starts before the Spring context, so pattern converters use only
bootstrap-safe mandatory rules and a recursion guard; they never inject Spring
beans. Boot JSON does not use pattern converters, so `PlatformLogger` never
passes a raw throwable: it emits a detached bounded `SanitizedThrowable`
snapshot as sanitized fields. Fragments explicitly bridge
`platform.logging.structured.format` to Boot's lowercase `ecs`, `gelf` or
`logstash`; TEXT uses `PatternLayoutEncoder`.

Auto-configuration order is properties, masking, crypto, context, structured
logging, aspects, audit, metrics, then optional servlet/reactive/Logback. Every
default contract backs off; no component scanning is used.

Auto-configurations declare explicit `before`/`after` relationships. Strategy
registries receive defaults and consumer extensions together; adding one custom
strategy does not disable secure defaults. Reserved event fields and the final
mandatory sanitization policy cannot be overridden by customizers.

## Security and verification

Production rejects disabled mandatory masking, insecure crypto, inline keys,
crypto annotations while crypto is disabled, and Jasypt without its classpath.
Mandatory field matching canonicalizes camel/kebab/snake/header names and cannot
be weakened by annotation, configuration, customizer or custom strategy.

Sanitization never invokes arbitrary getters or uncontrolled `toString()`.
Binary/stream/file/multipart/servlet/reactive/vendor HTTP objects are denied.
Regexes compile at startup and operate only on bounded strings. HASH uses
HMAC-SHA-256 for low-entropy PII in secure environments.

Raw bodies/arguments/results are off by default. Detached sanitized throwables
never retain the original cause/message. Converter errors emit only a constant
safe token with a recursion guard.

When no `KeyProvider` exists, explicit crypto operations fail with a stable
non-sensitive error; no local key is invented. `KeyMaterial` defensively copies
secret bytes, carries purpose/algorithm/alias/version, has explicit destruction,
and has no secret-derived `toString`, equality or hash. Key caching is bounded,
TTL-based, clearable and destroys evicted entries. It never caches plaintext.

Verification packages the module with JDK 25, validates auto-configuration and
configuration metadata, and scans output paths for secret leakage. Crypto and
logging behavior remain isolated behind feature-owned internal adapters.
