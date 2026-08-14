# Platform Service Exchange

## 1. Overview

`platform-service-exchange` cung cấp registry động cho nhiều outbound client có
identity ổn định. Platform sở hữu tên client, mapping transport, behavior,
logging/audit/fallback và integration conventions. Spring Boot sở hữu HTTP
infrastructure, SSL Bundle, observations; Resilience4j sở hữu policy registry;
Jasypt sở hữu giải mã property ở `Environment`.

Module hỗ trợ `WEBCLIENT`, `RESTCLIENT` và compatibility bridge gRPC. Không tạo
HTTP engine, pool, TLS context, OpenTelemetry SDK hay resilience registry riêng.

## 2. Architecture

```text
platform.service-exchange.clients.* -> named registry -> Boot WebClient/RestClient builder
resilience4j.*                       -> native named registries
management.*                         -> Micrometer observation/tracing
spring.ssl.bundle.*                  -> TLS/mTLS material
jasypt.encryptor.*                   -> Environment property decryption
```

Builder prototype do Boot quản lý được clone cho từng client, vì vậy codec,
customizer, SSL và observation instrumentation của Boot vẫn được giữ nguyên.
Registry validate và materialize các client enabled khi startup; thêm client chỉ
cần thêm YAML.

## 3. Native-properties philosophy

`platform.service-exchange.*` chỉ quản lý:

- `enabled`;
- tên client, `enabled`, `type`, `base-url` hoặc native `grpc-channel` reference;
- `resilience-instance`, `ssl-bundle` reference;
- `resilience-enabled`, `observability-enabled`;
- platform logging/audit behavior còn được compatibility API sử dụng.

Timeout, pool, proxy và transport tuning dùng Boot/native global customizer hoặc
application-provided registry.
Policy dùng `resilience4j.*`; tracing dùng `management.*`; TLS dùng
`spring.ssl.bundle.*`; crypto dùng `jasypt.encryptor.*`. Legacy key bị reject để
không bị Binder bỏ qua âm thầm.

## 4. Enable/disable

```yaml
platform:
  service-exchange:
    enabled: true
```

Khi `enabled=false`, module không tạo registry, platform wrappers, audit hay
observability beans. Client riêng có thể đặt `enabled=false`; `find` trả empty và
`get` ném lỗi rõ ràng.

## 5. Multiple named clients

```yaml
platform:
  service-exchange:
    clients:
      esb:
        type: webclient
        base-url: https://esb.company.vn
        resilience-instance: esb
        ssl-bundle: internal-ca
      crm:
        type: webclient
        base-url: https://crm.company.vn
        resilience-instance: crm
      payment:
        type: restclient
        base-url: https://payment.company.vn
        resilience-instance: payment
        ssl-bundle: payment-ca
```

Default `type` là `WEBCLIENT`; default resilience và observability đều bật.
Client name phải low-cardinality và là identity được dùng trong errors,
observations, resilience mapping, logs và audit.

## 6. WebClient

```java
ReactiveServiceExchangeClient crm = registry.get("crm", ReactiveServiceExchangeClient.class);
Mono<Customer> customer = crm.get("/v1/customers/42", Customer.class);
```

Reactive API không bị ép thành blocking API. `WebClient.Builder` là prototype
được Boot quản lý; module không gọi `WebClient.builder()`.

## 7. RestClient

```java
BlockingServiceExchangeClient payment =
    registry.get("payment", BlockingServiceExchangeClient.class);
PaymentResponse result = payment.post("/v1/payments", request, PaymentResponse.class);
```

`RestClient.Builder` được Boot clone theo named client. Compatibility API
`HttpExchangeOperations` vẫn resolve cùng named infrastructure, không tạo stack
thứ hai.

## 8. Client registry

```java
ServiceExchangeClient client = registry.get("esb");
Optional<ServiceExchangeClient> optional = registry.find("legacy");
boolean configured = registry.contains("payment");
```

Registry có thể được application thay hoàn toàn; default bean luôn back off.
Absolute URI, network-path (`//host`), user-info và fragment bị chặn để tránh
bypass base URL/SSRF policy. Base URL chỉ được là HTTP(S) origin an toàn.

Redirect phải tắt bằng native Boot policy để không vượt named origin:

```yaml
spring:
  http:
    clients:
      redirects: dont-follow
```

## 9. Client customizer

Application có thể cung cấp nhiều bean ordered:

```java
@Bean
@Order(100)
ServiceExchangeClientCustomizer commonHeaders() {
    return new ServiceExchangeClientCustomizer() {
        public boolean supports(String name) { return true; }
        public void customize(ServiceExchangeClientCustomization client) {
            client.defaultHeader("X-Platform-Client", client::clientName);
        }
    };
}
```

`supports(name)` cho cả global (`true`) và named customizer; `Ordered/@Order`
quyết định thứ tự ổn định. SPI không expose WebClient/RestClient type.

## 10. Authentication customizer

OAuth2/API key header thuộc application customizer. Advanced request signing dùng
application-provided registry hoặc native client customization. Không đặt
secret header trong `platform.service-exchange.clients.*` và không hard-code
partner authentication trong platform.

```java
@Bean
ServiceExchangeClientCustomizer paymentAuthentication(PaymentTokenProvider tokens) {
    return new ServiceExchangeClientCustomizer() {
        public boolean supports(String name) { return "payment".equals(name); }
        public void customize(ServiceExchangeClientCustomization client) {
            client.defaultHeader("Authorization", tokens::authorizationHeader);
        }
    };
}
```

## 11. SSL Bundle

```yaml
spring:
  ssl:
    bundle:
      jks:
        internal-ca:
          truststore:
            location: ${INTERNAL_TRUSTSTORE}
            password: ${INTERNAL_TRUSTSTORE_PASSWORD}

platform:
  service-exchange:
    clients:
      esb:
        base-url: https://esb.company.vn
        ssl-bundle: internal-ca
```

Client chỉ giữ bundle reference. Bundle không tồn tại làm startup fail; module
không hỗ trợ trust-all hay tắt hostname verification.

## 12. Proxy

Không có `platform.service-exchange.*.proxy`. Dùng Boot/native HTTP client
builder customization. Với proxy khác nhau theo client, implement ordered named
customizer và lấy credential từ secret provider; không đổi JVM system property.

## 13. Timeout

Không có platform timeout mirror. RestClient/WebClient transport timeout dùng
Boot/native HTTP-client configuration/customizer. Reactive logical timeout dùng
named `resilience4j.timelimiter.instances.*`; gRPC dùng native channel/deadline.
Transport timeout và TimeLimiter là hai boundary khác nhau.

## 14. Connection pool

Pool do HTTP implementation mà Boot chọn sở hữu. Nếu cần pool riêng theo client,
application cấu hình connector/request factory trong named customizer. Public API
không expose Reactor Netty hoặc Apache HttpClient types.

## 15. Observability

Boot instrumentation của WebClient/RestClient được giữ. Platform bổ sung
observation `platform.service.exchange` với low-cardinality keys
`client.name` và `http.method`; không tag URL đầy đủ, trace ID, request ID hay
payload. Có thể tắt wrapper theo client bằng `observability-enabled: false`.

```yaml
management:
  observations:
    enable:
      http.client.requests: true
      platform.service.exchange: true
```

## 16. Distributed tracing

Micrometer Tracing/OpenTelemetry được application/Boot cấu hình. Module không
tạo SDK hoặc trace ID và không overwrite `traceparent`, baggage hay active span.

```yaml
management:
  tracing:
    sampling:
      probability: 0.1
```

## 17. Correlation ID

Compatibility operations giữ `X-Correlation-Id`/`X-Request-Id` từ
`RequestContextProvider` và trace context platform. Native propagation header
vẫn do framework quản lý; customizer có thể áp company headers cho client API.

## 18. Logging/masking

Logging/audit hiện hữu được giữ và delegate masking sang `platform-logging`
`DataMaskingService`. Authorization, cookies, tokens, credentials và PII không
được log plaintext. Body logging phải opt-in, bounded, mask trước truncate; binary,
multipart, resource và streaming body không được buffer để log.

## 19. cURL logging

cURL mặc định tắt. Compatibility setting:

```yaml
platform:
  service-exchange:
    clients:
      payment:
        logging:
          curl-enabled: false
          max-body-length: 4096
```

Output được shell-quote, mask header/query/body và không đọc file/stream.

## 20. Resilience4j

Thêm `resilience4j-spring-boot4` trong application khi client bật resilience.
Module lookup named instances đã được Boot bind; không tự tạo registry/config.
Instance thiếu hoặc predicate không an toàn làm startup fail.

Order logical call:

```text
RateLimiter -> Bulkhead -> CircuitBreaker -> Retry -> transport
```

Reactive WebClient còn có outer `TimeLimiter`. Admission dùng zero wait để tránh
thread pile-up. Named clients không dùng chung state nếu tên instance khác nhau.

## 21. Circuit Breaker

```yaml
resilience4j:
  circuitbreaker:
    instances:
      esb:
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        record-exception-predicate: com.company.platform.exchange.api.resilience.OutboundCircuitBreakerPredicate
```

Predicate bỏ qua lỗi programming và business 4xx; chỉ remote/server/transient
failure được record.

## 22. Retry

```yaml
resilience4j:
  retry:
    instances:
      esb:
        max-attempts: 3
        wait-duration: 500ms
        retry-exception-predicate: com.company.platform.exchange.api.resilience.OutboundRetryPredicate
```

Platform cap `max-attempts` ở 3. Chỉ connection/timeout/408/502/503/504 được
retry mặc định. HTTP 429 chỉ nên opt-in bằng application predicate có xử lý
`Retry-After` bounded. Minimal `post` API luôn non-retryable; compatibility API chỉ retry
POST/PATCH khi request có explicit idempotency guarantee.

## 23. Rate Limiter

```yaml
resilience4j:
  ratelimiter:
    instances:
      esb:
        limit-for-period: 100
        limit-refresh-period: 1s
        timeout-duration: 0
```

Zero wait là bắt buộc để admission fail-fast.

## 24. Bulkhead

```yaml
resilience4j:
  bulkhead:
    instances:
      esb:
        max-concurrent-calls: 50
        max-wait-duration: 0
```

## 25. TimeLimiter

```yaml
resilience4j:
  timelimiter:
    instances:
      esb:
        timeout-duration: 5s
        cancel-running-future: true
```

TimeLimiter được áp cho reactive client; blocking RestClient phải dùng native
transport timeout/deadline thích hợp.

## 26. Fallback

`OutboundFallbackHandler<T>` hiện hữu là application extension. Platform không
return `null`, `{}` hay business response mặc định. Fallback nằm ngoài transport
pipeline, explicit trong logs/audit/metrics, và handler lỗi không bị nuốt.

## 27. Error handling

Client API normalize vendor failure thành `ServiceExchangeClientException`, giữ
root cause và expose an toàn `clientName`, method, HTTP status, retryable. Message
không chứa credential hoặc body. Compatibility API tiếp tục dùng exception
hierarchy chi tiết cho HTTP/gRPC. Streaming/file lớn nên dùng native client trực
tiếp để giữ streaming semantics.

## 28. Jasypt

Service Exchange không decrypt secret. Application opt-in Jasypt starter; Jasypt
wrap `Environment` trước khi Boot bind SSL, proxy, OAuth hay application secret.

```yaml
jasypt:
  encryptor:
    password: ${JASYPT_ENCRYPTOR_PASSWORD}
```

## 29. `ENC(...)`

```yaml
external:
  esb:
    api-key: ENC(...)
```

Customizer inject property đã decrypt. Không đặt master password trong source,
command line hoặc log; dùng secret manager/protected environment.

## 30. Complete multi-client example

```yaml
platform:
  service-exchange:
    enabled: true
    clients:
      esb:
        type: webclient
        base-url: https://esb.company.vn
        resilience-instance: esb
        ssl-bundle: internal-ca
      crm:
        type: webclient
        base-url: https://crm.company.vn
        resilience-instance: crm
      payment:
        type: restclient
        base-url: https://payment.company.vn
        resilience-instance: payment
        ssl-bundle: payment-ca
      scoring:
        base-url: https://scoring.company.vn
        resilience-enabled: false
      notification:
        base-url: https://notification.company.vn
        resilience-instance: notification
```

Mỗi enabled resilience client cần cùng named instance cho circuit breaker,
retry, rate limiter và bulkhead; WebClient cần thêm time limiter. Nếu
`resilience-instance` trống, convention dùng chính client name.

## 31. Migration old -> new

| Old platform property | New native owner |
|---|---|
| `clients.*.protocol/http.base-url` | `clients.*.type/base-url` |
| `clients.*.http.*timeout` | Boot HTTP client/customizer |
| `clients.*.http.pool.*` | native connector/request factory |
| `clients.*.proxy.*` | named application customizer |
| `clients.*.ssl.*` | `spring.ssl.bundle.*` + `ssl-bundle` reference |
| `clients.*.resilience.*` | `resilience4j.*.instances.*` |
| exchange tracing/metrics properties | `management.*` |
| exchange crypto properties | `jasypt.encryptor.*` |
| `clients.*.grpc.*` | `spring.grpc.client.channels.*` + `grpc-channel` reference |

Không có compatibility binding cho property cũ: startup fail-fast giúp phát hiện
YAML chưa migrate. Public compatibility operations/fallback/audit contracts vẫn
được giữ và chạy trên named native infrastructure.

## gRPC compatibility

```yaml
platform:
  service-exchange:
    clients:
      inventory-grpc:
        type: grpc
        grpc-channel: inventory

spring:
  grpc:
    client:
      channels:
        inventory:
          address: dns:///inventory.internal:9090
```

Spring gRPC sở hữu channel, TLS, negotiation, keepalive và lifecycle. Registry
chỉ giữ reference; không đóng channel do Spring tạo. API mới phải truyền deadline
dương rõ ràng; overload compatibility cũ dùng budget bounded 5 giây.
