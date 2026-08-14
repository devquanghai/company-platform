# Platform Service Exchange Design

## Scope

Module là integration/convention layer cho named outbound clients. Nó không sở
hữu HTTP engine, connection pool, TLS material, OpenTelemetry SDK, Resilience4j
registry, Jasypt decryptor hoặc business protocol.

Runtime Maven dùng JDK 25 theo parent enforcer; bytecode hiện vẫn theo repository
baseline `release 21` cho đến khi parent nâng compile target.

## Ownership

| Capability | Owner |
|---|---|
| client name/type/base URL and mapping | platform registry |
| WebClient/RestClient infrastructure | Spring Boot builders |
| gRPC channel transport/lifecycle | Spring gRPC |
| TLS/mTLS | Spring SSL Bundles |
| retry/CB/rate limit/bulkhead/time limit | native Resilience4j registries |
| tracing/transport observation | Boot + Micrometer |
| secret property decryption | Jasypt Environment integration |
| safe masking | platform-logging |
| audit/logging/fallback conventions | platform-service-exchange |

## Execution path

```text
application.yml
  -> ServiceExchangeProperties (minimal identity/behavior)
  -> startup validator
  -> DefaultServiceExchangeClientRegistry
  -> clone Boot-managed RestClient.Builder or WebClient.Builder
  -> apply SSL Bundle reference and ordered application customizers
  -> named native Resilience4j decorators
  -> Boot transport observation/tracing
  -> remote service
```

Registry creates each enabled client at startup. Invalid URL, disabled lookup,
missing SSL Bundle, unavailable Boot builder, missing named resilience instance
or unsafe retry/circuit predicate fails with a sanitized configuration error.

## APIs and compatibility

`ServiceExchangeClientRegistry` exposes the neutral marker plus separate
`BlockingServiceExchangeClient` and `ReactiveServiceExchangeClient`; reactive and
blocking semantics are not mixed. WebClient/RestClient customizers are named and
ordered.

Existing `HttpExchangeOperations`, gRPC operations, audit and fallback contracts
remain compatibility APIs. HTTP compatibility operations resolve the same named
RestClient infrastructure. Spring-owned gRPC channels are referenced, never
closed by platform.

## Resilience

Named instance defaults to client name. Blocking order is:

```text
RateLimiter -> Bulkhead -> CircuitBreaker -> Retry -> transport
```

Reactive adds an outer TimeLimiter. Retry is bounded to three attempts and must
use `OutboundRetryPredicate`; circuit breaker must use
`OutboundCircuitBreakerPredicate`. Admission wait is zero. Minimal POST calls are
never retryable; compatibility request APIs retain explicit idempotency support.

No resilience fallback returns a business value by default. Application handler
selection is explicit and observable.

## Observability and security

Boot builder instrumentation preserves trace propagation. Platform observation
adds only `client.name` and `http.method`; URL, payload, request ID and trace ID
are not meter tags. Active trace IDs are never generated or overwritten.

Logging delegates to platform-logging masking and never emits a partial/truncated
value. Oversized values are omitted as one safe sentinel; binary, multipart,
resource and stream bodies are not buffered. cURL is disabled by default.
Only relative request URIs are accepted. Exception messages contain no body,
credential, header value or full dynamic target.

## Native configuration boundary

- `platform.service-exchange.*`: identity, mapping, platform behavior.
- `resilience4j.*`: resilience policy and metrics/events.
- `management.*`: observations and tracing.
- `spring.ssl.bundle.*`: key/trust material.
- `spring.grpc.client.*`: gRPC transport.
- `jasypt.encryptor.*`: property decryption.

Legacy duplicated transport/resilience/SSL keys are rejected rather than ignored.
