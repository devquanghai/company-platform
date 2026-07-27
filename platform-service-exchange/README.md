# Platform Service Exchange

Thư viện dùng chung cho outbound REST qua HTTP/HTTPS và gRPC qua HTTP/2. Module
quản lý named client, connection/channel lifecycle, resilience, fallback,
masking, audit và observability trong một artifact; không chứa business `.proto`
hoặc endpoint cụ thể.

## Kiến trúc

Public contract nằm trong `api`, orchestration nằm trong `application`, transport
nằm trong `adapter.outbound`, policy nằm trong `resilience`, còn Spring Boot
wiring chỉ nằm trong `autoconfigure`. Chi tiết quyết định thiết kế xem
[`docs/platform-service-exchange-design.md`](../docs/platform-service-exchange-design.md).

REST dùng Spring `RestClient` và Apache HttpClient 5. gRPC dùng generated stub của
service tiêu thụ, Spring gRPC `GrpcChannelFactory`, `ChannelBuilderOptions` và
shaded Netty. HTTPS không phải gRPC: REST có thể dùng HTTP hoặc HTTPS; gRPC luôn
dùng HTTP/2 và có thể plaintext, TLS hoặc mTLS.

## Maven

```xml
<dependency>
    <groupId>com.company.platform</groupId>
    <artifactId>platform-service-exchange</artifactId>
</dependency>
```

Version được quản lý bởi platform parent. Không thêm version vào service con.

## Auto-configuration

Auto-configuration đăng ký bằng
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`,
không component scan. Tắt toàn bộ module:

```yaml
platform:
  service-exchange:
    enabled: false
```

Mọi extension bean mặc định đều back off khi application cung cấp bean riêng.

## Named REST client

```yaml
platform:
  service-exchange:
    enabled: true
    source-application: payment-service
    clients:
      payment-rest:
        enabled: true
        protocol: HTTP
        http:
          base-url: https://payment.internal
          connect-timeout: 2s
          connection-request-timeout: 2s
          response-timeout: 8s
          allow-absolute-uri: false
          pool:
            max-total: 200
            max-per-route: 50
            validate-after-inactivity: 5s
            time-to-live: 5m
            evict-idle-connections-after: 60s
        ssl:
          enabled: true
          bundle: payment-client
          hostname-verification-enabled: true
          trust-all: false
```

Caller chỉ truyền relative path theo mặc định. Absolute URI, `//host`, user-info
và fragment bị chặn để không bỏ qua named-client policy hoặc tạo SSRF.

## REST usage

```java
@Service
@RequiredArgsConstructor
public class PaymentGateway {
    private final HttpExchangeOperations http;

    public PaymentResponse createPayment(PaymentRequest request) {
        return http.post(
            "payment-rest",
            "/api/v1/payments",
            request,
            PaymentResponse.class
        ).body();
    }
}
```

Generic response:

```java
ExchangeResponse<List<CustomerResponse>> response =
    http.get(
        "customer-rest",
        "/api/v1/customers",
        Map.of("status", "ACTIVE"),
        HttpHeaders.EMPTY,
        new ParameterizedTypeReference<>() {}
    );
```

`ExchangeRequest` builder hỗ trợ headers, cookies, query/path variable, content
type, accept, idempotency key, audit attributes và controlled overrides.

## gRPC

```yaml
platform:
  service-exchange:
    clients:
      customer-grpc:
        protocol: GRPC
        grpc:
          address: dns:///customer-grpc.internal:9090
          negotiation-type: TLS
          default-deadline: 5s
          max-inbound-message-size: 8MB
          keep-alive-time: 30s
          keep-alive-timeout: 10s
        ssl:
          enabled: true
          bundle: customer-grpc-client
```

```java
CustomerServiceGrpc.CustomerServiceBlockingStub stub =
    grpcClientFactory.createStub(
        "customer-grpc",
        CustomerServiceGrpc::newBlockingStub
    );

CustomerResponse response = grpcCalls.execute(
    "customer-grpc",
    "customer.CustomerService",
    "GetCustomer",
    () -> stub.getCustomer(request)
);
```

`GrpcCallOperations` dành cho unary synchronous call. Generated streaming stub
được dùng trực tiếp vì streaming có lifecycle và retry semantics khác.

### Plaintext, TLS và mTLS

- `PLAINTEXT`: chỉ dùng trong network tin cậy hoặc local test.
- `TLS`: dùng platform trust hoặc trust material trong SSL Bundle.
- `MTLS`: SSL Bundle phải có cả key manager và trust manager.

Deadline bao phủ toàn bộ logical call, bao gồm retry. Per-call override chỉ được
giảm deadline đã cấu hình.

## Spring SSL Bundle

```yaml
spring:
  ssl:
    bundle:
      jks:
        payment-client:
          keystore:
            location: classpath:tls/client.p12
            password: ${CLIENT_KEYSTORE_PASSWORD}
            type: PKCS12
          truststore:
            location: classpath:tls/truststore.p12
            password: ${CLIENT_TRUSTSTORE_PASSWORD}
```

Không commit password. Bundle được kiểm tra tồn tại lúc startup.

## Proxy per client

```yaml
proxy:
  enabled: true
  scheme: http
  host: proxy.internal
  port: 8080
  username: ${PAYMENT_PROXY_USERNAME:}
  password: ${PAYMENT_PROXY_PASSWORD:}
  non-proxy-hosts:
    - localhost
    - "*.internal"
```

Không thay đổi JVM system properties. REST dùng proxy của Apache transport; gRPC
shaded Netty hỗ trợ HTTP CONNECT. Scheme/transport không hỗ trợ sẽ fail fast.
`ClientProxyCustomizer` cho phép thay đổi endpoint proxy qua model trung lập,
không expose Apache API.

## Resilience

Pipeline logic:

```text
RateLimiter -> Bulkhead -> CircuitBreaker -> Retry -> transport
```

Circuit breaker ghi nhận kết quả logical call sau retry. Fallback nằm ngoài
pipeline và không biến lỗi transport thành circuit-breaker success.

```yaml
resilience:
  enabled: true
  retry:
    enabled: true
    max-attempts: 3
    wait-duration: 300ms
    retry-http-statuses: [408, 425, 429, 500, 502, 503, 504]
    retry-grpc-statuses: [UNAVAILABLE, RESOURCE_EXHAUSTED, DEADLINE_EXCEEDED]
    retry-methods: [GET, HEAD, OPTIONS]
  circuit-breaker:
    enabled: true
    sliding-window-size: 20
    minimum-number-of-calls: 10
    failure-rate-threshold: 50
    wait-duration-in-open-state: 30s
  rate-limiter:
    enabled: true
    limit-for-period: 100
    limit-refresh-period: 1s
  bulkhead:
    enabled: false
    max-concurrent-calls: 50
```

POST/PATCH không retry mặc định. Chỉ retry khi có idempotency key, caller đánh
dấu idempotent, hoặc client opt-in rõ ràng. Status nghiệp vụ 400/401/403/404/
409/422 và gRPC `INVALID_ARGUMENT`, `UNAUTHENTICATED`, `PERMISSION_DENIED`,
`NOT_FOUND` không retry mặc định.

## Custom fallback

```java
@Component
@ExchangeFallback(client = "payment-rest")
public class PaymentFallback
        implements OutboundFallbackHandler<PaymentResponse> {

    @Override
    public Class<PaymentResponse> responseType() {
        return PaymentResponse.class;
    }

    @Override
    public boolean supports(FallbackContext context) {
        return context.getClientName().equals("payment-rest");
    }

    @Override
    public PaymentResponse fallback(FallbackContext context) {
        return PaymentResponse.pending();
    }
}
```

Registry fail startup khi registration trùng. Không có fallback thì rethrow
exception đã normalize; fallback lỗi được wrap thành `OutboundFallbackException`.

## Logging, cURL và masking

Ba logger logic là `OUTBOUND_CALL`, `OUTBOUND_CURL`, `OUTBOUND_AUDIT`. cURL dùng
shell quoting, URL/header/body đã masking và truncation. Binary, stream và
`Resource` không bị đọc để log. Regular cURL không đại diện đúng gRPC; chỉ tạo
grpcurl khi application có đủ unary descriptor và JSON payload an toàn.

Header nhạy cảm mặc định gồm Authorization, Proxy-Authorization, Cookie,
Set-Cookie và API/token headers. Field mặc định gồm password, secret, token,
clientSecret, privateKey, cardNumber, cvv, pin và accountNumber. Có thể mở rộng
danh sách theo client.

## Outbound audit

Event immutable:

- `OutboundCallStartedEvent`
- `OutboundCallAttemptEvent` (opt-in)
- `OutboundCallCompletedEvent`
- `OutboundCallFailedEvent`

Mỗi logical call chỉ có một final event. Event không chứa raw credential hoặc raw
body mặc định. Default publisher dùng Spring `ApplicationEventPublisher`:

```java
@Component
public class OutboundAuditListener {
    @EventListener
    public void handle(OutboundCallCompletedEvent event) {
        // Persist, Kafka hoặc outbox.
    }
}
```

Override `OutboundCallEventPublisher` để dùng Kafka/database/outbox. `FAIL_OPEN`
là mặc định. `FAIL_CLOSED` chỉ nên dùng khi synchronous audit là yêu cầu nghiệp
vụ vì listener lỗi sẽ làm business call lỗi.

## Metrics và tracing

Khi có Micrometer, module đăng ký low-cardinality metrics:

- `platform.exchange.calls`
- `platform.exchange.call.duration`
- `platform.exchange.errors`
- `platform.exchange.retries`
- `platform.exchange.fallbacks`

Tags chỉ gồm client, protocol, method, outcome, status group, exception category
và fallback. Không dùng URI đầy đủ, customer ID, request ID hoặc trace ID làm tag.
Trace/request context từ `platform-core` được đưa vào response metadata và audit.

## Security warnings

- Không bật `allow-absolute-uri` nếu caller không được tin cậy.
- Không bật `trust-all` hoặc tắt hostname verification ở production.
- Trust-all cần thêm global `allow-insecure-ssl=true`, vẫn bị từ chối ở prod.
- Không ghi token, cookie, private key, proxy password hoặc raw PII vào log/event.
- Không retry non-idempotent request nếu không có idempotency contract.
- Không serialize configuration hoặc gọi `toString()` trên secret properties.

## Troubleshooting

- `CLIENT_NOT_FOUND`: kiểm tra đúng key dưới `clients`.
- `CLIENT_DISABLED`: client tồn tại nhưng `enabled=false`.
- `INVALID_CONFIGURATION`: kiểm tra protocol-specific `base-url`/`address`,
  timeout, proxy và SSL Bundle.
- TLS handshake: kiểm tra truststore, SAN/hostname và bundle key material.
- gRPC TLS yêu cầu shaded Netty transport; proxy gRPC hiện là HTTP CONNECT.
- Circuit open/rate limited: xem low-cardinality metrics và named policy.

## Migration

Thay `RestTemplate`, static HTTP helper hoặc client tự tạo connection bằng
`HttpExchangeOperations`. Di chuyển URL/timeout/SSL/retry vào named-client config.
Generated gRPC stub vẫn giữ nguyên; chỉ thay cách tạo channel bằng
`GrpcClientFactory` và bọc unary invocation bằng `GrpcCallOperations`.
