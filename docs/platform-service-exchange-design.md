# Platform Service Exchange Design

## 1. Scope and constraints

`platform-service-exchange` is one library artifact for outbound REST and gRPC
communication. It contains no business-domain protocol, generated gRPC stub,
application entry point, credential, or environment-specific endpoint.

The module uses Java 25, Spring Boot 4.0.7, Spring Framework 7.0.8, Spring
`RestClient`, Apache HttpClient 5, Spring gRPC, Resilience4j, and the repository
parent/BOM conventions. Dependency versions that are not managed by Spring Boot
are imported once by the root dependency management.

The implementation reuses these `platform-core` ports:

- `JsonMapperHelper` for Jackson 3 serialization.
- `TimeProvider` for timestamps.
- `RequestContextProvider` for request and correlation identifiers.
- `TraceContextProvider` for trace and span identifiers.
- Platform exception codes where their semantics match.

There is no reusable masking or outbound-call event publisher in
`platform-core`; this module therefore owns those narrowly scoped SPIs.

## 2. Architecture

```text
com.company.platform.exchange
├── api
│   ├── http                 public REST operations and request/response models
│   └── grpc                 public channel/stub and call operations
├── domain
│   ├── exception            normalized outbound failures
│   ├── model                protocol, metadata, retry/fallback context
│   └── policy               configuration-independent policy contracts
├── application
│   ├── port.out             internal transport ports
│   └── service              logical-call orchestration
├── adapter.outbound
│   ├── http                 RestClient, HttpClient5, URI and SSL adapters
│   ├── grpc                 Spring gRPC channel/interceptor adapters
│   └── event                Spring application-event publisher
├── resilience              shared policy pipeline and fallback registry
├── audit                    immutable outbound event hierarchy and publisher SPI
├── observability            masking, command rendering, metrics and observations
├── autoconfigure           Boot auto-configuration only
└── support                  small internal validation and sanitization helpers
```

`api` never exposes Apache HttpClient types. gRPC intentionally exposes
`io.grpc.Channel` and `AbstractStub` because generated consuming-service stubs
require those standard gRPC contracts.

## 3. Public API

### REST

`HttpExchangeOperations` offers GET, POST, PUT, PATCH, DELETE, and a general
`exchange` operation. `ExchangeRequest` is an immutable class with a builder and
defensive copies for headers, query parameters, path variables, cookies, audit
attributes, and controlled overrides. `ExchangeResponse<T>` contains transport
neutral response data and safe `OutboundCallMetadata`.

Paths are relative to the named client's `base-url`. Absolute and network-path
(`//host`) references are rejected unless `http.allow-absolute-uri` is explicitly
enabled for that client. Authority, user info and fragments are always rejected,
and the fully expanded URI is canonicalized and checked again before invocation.

### gRPC

`GrpcClientFactory` returns named shared channels and creates generated stubs
through a caller supplied `Function<Channel,S>`. `GrpcCallOperations` wraps a
unary synchronous generated-stub invocation in deadline, resilience,
observability, audit, and fallback behavior. Streaming uses generated stubs
directly and is rejected by this executor. The library does not define business
`.proto` files.

## 4. Configuration model

`ServiceExchangeProperties` binds `platform.service-exchange` and owns a map of
`ClientProperties`. Each client composes focused property classes for HTTP,
gRPC, SSL, proxy, logging, audit, and resilience. Global defaults are merged by
`ClientConfigurationResolver`; the bound objects are never mutated after
resolution.

Validation has two levels:

1. Jakarta Bean Validation checks local numeric and duration constraints.
2. `ServiceExchangePropertiesValidator` checks cross-field rules: valid client
   names, protocol-specific target, positive timeout, SSL bundle existence,
   proxy coordinates, retry/rate-limit ranges, and insecure TLS policy.

Invalid configuration fails application startup with the client name and field,
but never includes secret values.

## 5. Named-client lifecycle

`ExchangeClientRegistry` exposes immutable, defensively copied runtime snapshots;
mutable bound properties and secret values never become registry values.
`HttpClientRegistry` and `GrpcChannelRegistry` use concurrent maps and
single-initialization semantics. A failed lazy creation is removed rather than
retained as a poisoned entry. Disabled clients are retained as definitions so
lookup raises `ClientDisabledException`, but no transport resource is created.

Eager initialization validates and builds enabled clients at startup when
configured; lazy initialization builds on first lookup. HTTP clients and
connection managers are closed once during context shutdown. Spring gRPC 1.0.3
`GrpcChannelFactory` is the sole owner of channels it creates.
`GrpcChannelRegistry` caches references but never closes them, preventing a
double shutdown. Application-supplied channels are also non-owned.

## 6. Transport adapters

### REST

One `CloseableHttpClient`, pooling connection manager, request factory, and
`RestClient` are created per enabled HTTP client. Connection limits, TTL,
validation-after-inactivity, response/connect/pool-acquisition timeouts, idle
eviction, proxy routing, and TLS strategy are client-scoped.

Spring SSL Bundles supply key and trust material for TLS/mTLS. Default JVM trust
is used when no bundle is selected. Trust-all is a separate strategy requiring
both `ssl.trust-all=true` and the global insecure override; hostname verification
remains enabled unless separately and explicitly disabled.

### gRPC

Named channels use Spring gRPC 1.0.3
`GrpcChannelFactory.createChannel(address, ChannelBuilderOptions)`.
Per-channel options provide interceptors and a
`GrpcChannelBuilderCustomizer` for negotiation, message/keep-alive limits,
authority override and a standard gRPC `ProxyDetector`.

For shaded Netty, SSL Bundle stores are adapted to shaded Netty
`GrpcSslContexts.forClient()` key/trust manager inputs; protocols and ciphers are
copied from the bundle. Plaintext selects plaintext negotiation, TLS without a
bundle uses platform trust, and a bundle with both key and trust material enables
mTLS. Unsupported proxy scheme/authentication or non-Netty transport fails
startup. `ClientProxyCustomizer` uses a transport-neutral customization context;
Apache types are never public.

## 7. Resilience

`ResilienceExecutor` is transport-neutral. Registries hold one policy instance
per named client. The logical-call pipeline is:

```text
RateLimiter -> Bulkhead -> CircuitBreaker -> Retry -> transport
```

The circuit breaker records the final result after retry, not each transport
attempt. HTTP applies request-scoped timeout through an internal Apache request
configuration without exposing Apache types. gRPC installs one cancellable gRPC
`Context` deadline around the complete logical call; retry observes its remaining
budget. A call-level override may only reduce configured limits by default.

`RetryDecisionPolicy` receives normalized method/status/exception/idempotency and
named-client rules. PUT and DELETE follow configured idempotency rules. POST and
PATCH are not retryable without an idempotency key,
explicit idempotent indication, or an explicit client opt-in. Known business
HTTP/gRPC statuses are ignored by default. `Retry-After` is bounded by the
configured maximum wait and remaining logical deadline. Circuit breaker
predicates ignore configured business failures.

Fallback is outside every resilience decorator and is invoked only after pipeline
exhaustion, so fallback success is not a transport success.
`OutboundFallbackHandler<T>` declares `Class<T> responseType()`. The registry keys
handlers by client, operation and response type and fails startup for ambiguous
registrations. No handler means rethrowing the normalized exception; a failing
handler becomes `OutboundFallbackException`.

## 8. Logging, masking, and command rendering

`OutboundDataMasker` is an overrideable SPI. The default implementation:

- compares header/query/field names case-insensitively;
- removes credentials and cookies;
- recursively masks Jackson JSON fields;
- truncates text after masking;
- classifies binary, multipart, resource, stream, and unknown bodies as
  non-loggable.

`CurlGenerator` shell-quotes each argument and never consumes streams or files.
gRPC logs structured metadata and only emits a `grpcurl` command when a
caller-provided safe JSON payload and complete unary method metadata exist.

Logical completion is logged once by the orchestration layer through `OUTBOUND_CALL`,
`OUTBOUND_CURL`, and `OUTBOUND_AUDIT` loggers. Serialization or logging failures
are isolated. Transport interceptors collect attempt data but never log a second
logical failure.

## 9. Audit and observability

The immutable event hierarchy consists of started, optional attempt, completed,
and failed events sharing `OutboundCallEvent`. The state machine publishes one
Started event, optional Attempt events, then exactly one final event. Transport
or fallback success publishes Completed (retaining original failure metadata);
no fallback or fallback failure publishes Failed. Events contain source
application, safe targets, identifiers, statuses, counts, circuit/rate-limit/
timeout flags, timing, payload hashes, and immutable sanitized attributes.

`SpringOutboundCallEventPublisher` delegates to `ApplicationEventPublisher`.
Publishing is caught at the orchestration boundary and is fail-open by default.
Fail-closed is explicit and only guarantees synchronous listener propagation.

If Micrometer is present, the orchestration layer owns
`ExchangeObservationSupport` and creates
`platform.exchange.outbound` observations and records low-cardinality meters.
Trace/request context providers populate logs, responses and events. Full URI,
request ID, trace ID, and customer data are never meter tags. Existing transport
instrumentation is reused rather than creating a second transport span.

## 10. Auto-configuration and extension points

Auto-configuration is registered only in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
There is no component scan. Global disable prevents all exchange beans.
Transport configurations are classpath-conditioned and create only light
registries/factories; arbitrary client maps are inspected at runtime and disabled
clients allocate no transport resources. Child configurations repeat the global
guard and declare explicit auto-configuration ordering.

Every default extension bean backs off:

- `HttpExchangeOperations`
- `GrpcClientFactory`
- `GrpcCallOperations`
- `RetryDecisionPolicy`
- `ResilienceExecutor`
- `OutboundFallbackRegistry`
- `OutboundCallEventPublisher`
- `CurlGenerator`
- `OutboundDataMasker`
- `ClientProxyCustomizer`

## 11. Security decisions

- Relative-only REST targets by default; network-path references, authority,
  user-info and fragments are rejected.
- Trust-all and disabled hostname verification default to false and are forbidden
  in production unless an explicit global override is also set.
- Secrets are held without generated `toString`, excluded from metadata default
  examples, and never included in configuration/error rendering.
- Proxy behavior is per client; no JVM global proxy property is changed.
- Non-idempotent methods do not retry by default.
- Raw payloads are absent from audit and disabled in logs by default.
- Exception target, headers, trailers, descriptions, and bodies are sanitized
  before storage.

## 12. Test strategy

Unit tests focus on URI rules, configuration validation, merge semantics, retry
decision branches, pipeline ordering, fallback selection, masking, truncation,
shell escaping, event finality, exception normalization, and lifecycle ownership.

`ApplicationContextRunner` tests global enable/disable, property validation,
classpath conditions, custom-bean back-off, disabled clients, multiple clients,
auto-configuration imports, and generated configuration metadata.

Integration tests use a local HTTP server and in-process gRPC server for verbs,
generic response types, non-2xx responses, plaintext channels, status mapping,
deadline budget, interception, and lifecycle ownership. Generated test
certificates cover HTTPS SSL Bundle, gRPC TLS, and gRPC mTLS key/trust material.
Timing-sensitive resilience tests use small deterministic intervals or
controllable clocks, never long sleeps.

The module enforces at least 85% line and 75% branch coverage, with higher
focused coverage for policy, masking, audit, and resilience behavior.
