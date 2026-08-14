# Platform native configuration audit

## Dependency baseline

Dependency tree/effective POM được audit với reactor hiện tại:

| Component | Version |
|---|---:|
| Java release | 25 |
| Spring Boot | 4.0.7 |
| Spring Framework | 7.0.8 |
| Spring Data Redis | 4.0.6 |
| Lettuce | 6.8.2.RELEASE |
| Caffeine | 3.2.4 |
| Spring Kafka | 4.0.6 |
| Kafka client | 4.1.2 |
| Spring AMQP | 4.0.4 |
| RabbitMQ client | 5.27.1 |
| Spring gRPC | 1.0.3 |
| Resilience4j | 2.4.0 |
| Micrometer | 1.16.6 |
| Apache HttpClient | 5.5.2 |

Đã kiểm tra generated metadata và public auto-configuration/API của đúng version:
`CacheProperties`, `RedisProperties`, `KafkaProperties`,
`RabbitProperties`, `HttpClientProperties`,
`HttpServiceClientProperties`, `GrpcClientProperties`, Resilience4j Boot 4
property classes/registries, Spring Kafka retry topic/error handler và Spring
AMQP listener/template retry.

## Property/class decisions

| Custom class/field group cũ | Owner thực sự | Native replacement | Action | Reason |
|---|---|---|---|---|
| `PlatformCacheProperties.enabled/annotationsEnabled` | Spring Cache | `@EnableCaching` | REMOVE | Native opt-in, không cần runtime flag |
| `CacheDefaultsProperties.ttl/cacheNullValues/keyPrefix` | Boot Cache | `spring.cache.redis.*` | REMOVE | Mirror trực tiếp |
| `CacheDefaultsProperties.maximumEntrySize` | Provider/application | Caffeine spec hoặc serializer validation code | REMOVE | Không phải shared property; không có implementation độc lập sau refactor |
| `CacheStoreProperties.provider`, stores map | Boot Cache | `spring.cache.type` | REMOVE | Một application chọn một provider |
| `NamedCacheProperties` cache names/store/ttl | Boot/Spring Data Redis | `spring.cache.cache-names`, `RedisCacheManagerBuilderCustomizer` | REMOVE | Native property/API |
| `RedisProperties` và serialization schema fields | Boot Redis/Spring Data Redis | `spring.data.redis.*`, `spring.cache.redis.*`, `RedisCacheConfiguration` | REMOVE | Connection/cache/serializer đều có native owner |
| `MultiLevelProperties`, `FallbackProperties` | Không còn requirement | none | REMOVE | Multi-level/fallback bị loại khỏi scope |
| `NegativeCacheProperties`, `StampedeProperties`, `TtlJitterProperties` | Application/provider API | Spring Cache sync/native loader/Redis TTL function khi cần | REMOVE | Không còn platform behavior cần bind |
| `LockProperties` | Coordination library/application | Native lock API + Boot `RedisConnectionFactory` | REMOVE | Lock không phải cache configuration |
| `ObservabilityProperties` (cache) | Boot/Micrometer | `management.observations.*`, cache metrics auto-config | REMOVE | Duplicate flags/instrumentation |
| `PlatformQueueProperties.enabled/allowNoop` | Boot conditional infrastructure | classpath + `spring.kafka.*` / `spring.rabbitmq.*` | REMOVE | Không cần provider switch/noop abstraction |
| `BrokerProperties` native connection/security fields | Boot Kafka/Rabbit | `spring.kafka.*`, `spring.rabbitmq.*` | REMOVE | Mirror infrastructure |
| `KafkaDestinationProperties` topic/partitions/replication/key | Spring Kafka/Kafka Admin | `NewTopic`, `TopicBuilder`, Kafka producer record | REMOVE | Native API; topology thuộc application |
| `RabbitDestinationProperties` exchange/queue/routing/binding | Spring AMQP | declarable `Queue`, `Exchange`, `Binding` beans | REMOVE | Native API |
| `KafkaSubscriptionProperties` group/concurrency/ack/poll | Boot/Spring Kafka | `spring.kafka.consumer/listener.*`, `@KafkaListener` | REMOVE | Mirror listener configuration |
| `RabbitSubscriptionProperties` listener fields | Boot/Spring AMQP | `spring.rabbitmq.listener.*`, `@RabbitListener` | REMOVE | Mirror listener configuration |
| `RetryProperties` (queue) attempts/backoff/exceptions/modes | Spring Kafka/Spring AMQP | retry-topic, `DefaultErrorHandler`, Boot Rabbit retry | REMOVE | Generic retry model làm mất provider semantics |
| `DeadLetterProperties` destination/suffix/headers | Spring Kafka/Spring AMQP | DLT recoverer/retry-topic; broker DLX/DLQ | REMOVE | Native API/topology |
| `TopologyProperties` declare/validate modes | Kafka Admin/Spring AMQP Admin | native admin beans and fail-fast settings | REMOVE | Wrapper quanh native admin |
| `SerializationProperties`, `MessageProperties` | Kafka/Rabbit serializers/converters | native serializers, deserializers, message converters | REMOVE | Native extension points |
| `DeliveryProperties` inbox/outbox/locks | Application persistence | transaction/outbox implementation in owning service | REMOVE | Không thể production-ready nếu module không sở hữu durable store |
| `ObservabilityProperties` (queue) | Boot/Micrometer | native Kafka/Rabbit observation + management config | REMOVE | Duplicate flags/timers/spans |
| `ServiceExchangeProperties.enabled/defaults/clients` | Boot HTTP/gRPC | HTTP service groups, gRPC named channels | REMOVE | Named registry đã có native owner |
| `HttpClientProperties.baseUrl/methods` | Boot HTTP service clients | `spring.http.serviceclient.<group>.*`, HTTP service interfaces | REMOVE | Native group/client model |
| `TimeoutProperties.connect/read` | Boot HTTP | `spring.http.clients.*`, group overrides | REMOVE | Mirror HTTP settings |
| `TimeoutProperties.execution` | Resilience4j | `resilience4j.timelimiter.*` | REMOVE | Timeout thuộc policy owner |
| `HttpPoolProperties` | HTTP implementation | Boot-selected request factory/client builder customizer | REMOVE | Native builder/configurer |
| `SslProperties` | Boot SSL | `spring.ssl.bundle.*`, HTTP/gRPC bundle reference | REMOVE | Không copy key/trust passwords |
| `ProxyProperties` | HTTP implementation | public native client builder/group configurer | REMOVE | API customization, không cần shared property mirror |
| `GrpcClientProperties` | Spring gRPC | `spring.grpc.client.channels.*` | REMOVE | Mirror named channel configuration |
| `RetryProperties` (exchange) | Resilience4j | `resilience4j.retry.configs/instances` | REMOVE | Native binding/registry |
| `CircuitBreakerProperties` | Resilience4j | `resilience4j.circuitbreaker.configs/instances` | REMOVE | Native binding/registry |
| `RateLimiterProperties` | Resilience4j | `resilience4j.ratelimiter.configs/instances` | REMOVE | Native binding/registry |
| `BulkheadProperties` | Resilience4j | `resilience4j.bulkhead.configs/instances` | REMOVE | Native binding/registry |
| `ResilienceProperties` | Resilience4j | named registries/annotations | REMOVE | Wrapper aggregate gây copy ba lớp |
| `LoggingProperties`, `AuditProperties` | Application/logging platform | HTTP interceptors + platform logging/audit contract | REMOVE | Không phải transport configuration chung |
| observability settings (exchange) | Boot/Micrometer | native HTTP observations + `management.observations.*` | REMOVE | Tránh duplicate timer/span |

Action `KEEP`: không có. Action `REFACTOR`: không có custom property DTO nào;
mọi cấu hình được refactor thẳng sang namespace/API native.

## Remaining custom properties

`platform.cache` và `platform.queue` chỉ còn selector `enabled/provider` do
platform sở hữu. Broker/cache infrastructure và tuning dùng namespace native.
`platform.service-exchange.*` không còn custom transport properties.

## Enterprise configuration rationale

- Giá trị durability/idempotence trong Kafka sample là production baseline;
  throughput-oriented batching, poll sizes và concurrency phải load test.
- Cache local luôn bounded và có expiration. Redis và Caffeine không đồng thời
  tạo layer/fallback.
- HTTP retry config chỉ liệt kê lỗi transient; không retry mọi exception hay 4xx.
- Circuit breaker/rate limiter/bulkhead/time limiter là shared native configs,
  nhưng application chỉ áp policy thực sự cần cho từng client.
- Credentials, trust material và broker endpoints luôn externalized.
