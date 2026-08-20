# Platform Cache

## 1. Overview

`platform-cache` chọn một provider và bổ sung `PlatformCacheOperations` trên
`CacheManager` do Spring Boot tạo. Module không tự dựng connection factory,
client Redis, `CacheManager`, pool hoặc topology.

Quy tắc ownership:

- `platform.cache.*`: chỉ bật/tắt và chọn provider của platform.
- `spring.cache.*`: cache names và cấu hình Redis/Caffeine của Spring Boot.
- `spring.data.redis.*`: connection, authentication, timeout, SSL, standalone,
  Sentinel, Cluster và Lettuce pool của Spring Boot.
- `jasypt.encryptor.*`: cấu hình native của Jasypt.

## 2. Enable/disable

```yaml
platform:
  cache:
    enabled: true
    provider: caffeine
```

`enabled=false` bridge sang `spring.cache.type=none`; platform không tạo facade.
Không cấu hình thêm `spring.cache.type`. Nếu hai selector mâu thuẫn, startup fail-fast.

## 3. Redis provider

```yaml
platform:
  cache:
    enabled: true
    provider: redis
spring:
  cache:
    cache-names: [customers, products]
    redis:
      time-to-live: 10m
      cache-null-values: false
      use-key-prefix: true
```

Spring Boot tạo `RedisCacheManager`; platform chỉ cung cấp JSON value serialization
an toàn thay cho Java serialization mặc định. Type metadata chỉ chấp nhận các
package `com.company`, `java.lang`, `java.time`, `java.util` và array; ứng dụng có
model ngoài allow-list phải cung cấp `RedisCacheConfiguration` riêng. Bean custom
của application luôn được ưu tiên.

## 4. Redis standalone

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      username: ${REDIS_USERNAME:}
      password: ENC(...)
      connect-timeout: 2s
      timeout: 2s
      lettuce:
        pool:
          max-active: 32
          max-idle: 16
          min-idle: 4
          max-wait: 2s
```

## 5. Redis Sentinel

```yaml
spring:
  data:
    redis:
      username: ${REDIS_USERNAME:}
      password: ENC(...)
      sentinel:
        master: mymaster
        nodes:
          - redis-sentinel-01:26379
          - redis-sentinel-02:26379
          - redis-sentinel-03:26379
```

## 6. Redis Cluster

```yaml
spring:
  data:
    redis:
      username: ${REDIS_USERNAME:}
      password: ENC(...)
      cluster:
        nodes:
          - redis-01:6379
          - redis-02:6379
          - redis-03:6379
        max-redirects: 3
```

## 7. Caffeine provider

```yaml
platform:
  cache:
    enabled: true
    provider: caffeine
spring:
  cache:
    cache-names: [customers, products]
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=10m
```

Luôn đặt `maximumSize`/`maximumWeight`; cấu hình unbounded bị từ chối. Khi không
khai báo spec, platform thêm native low-precedence default `maximumSize=10000`.

## 8. Spring Cache annotations

Module kích hoạt Spring Cache annotation infrastructure. Dùng native annotations:

```java
@Cacheable(cacheNames = "customers", key = "#customerId")
public Customer findCustomer(String customerId) { ... }
```

Application có thể cung cấp `CacheManager` hoặc `PlatformCacheOperations` riêng;
auto-configuration sẽ back off.

`getOrLoad`, bulk operations và Redis `clear` giữ semantics native của provider;
Redis clear dùng `SCAN` theo batch và là best-effort, không phải linearizable
barrier. Atomic/CAS/optimistic contracts cũ đã bị loại bỏ. Distributed lock vẫn
là extension API nhưng không có default adapter; consumer phải cung cấp một
implementation phân tán fail-closed nếu sử dụng.

## 9. Resilience

Resilience chỉ áp dụng cho provider Redis và dùng trực tiếp registries/properties
native của Resilience4j. Adapter là opt-in; application Redis cần khai báo:

```xml
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot4</artifactId>
</dependency>
```

Tên instance ổn định là `platformCacheRedis`; cả ba instance bên dưới là bắt
buộc khi dependency có mặt:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      platformCacheRedis:
        sliding-window-type: count_based
        sliding-window-size: 50
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 5
        register-health-indicator: true

  retry:
    instances:
      platformCacheRedis:
        max-attempts: 2
        wait-duration: 50ms

  bulkhead:
    instances:
      platformCacheRedis:
        max-concurrent-calls: 64
        max-wait-duration: 0
```

Platform cung cấp safe exception policy qua official Resilience4j customizers:

- retry chỉ áp dụng cho Redis read và chỉ retry lỗi connection/transient;
- `put`, `putIfAbsent`, `evict`, `clear` không bao giờ retry;
- circuit breaker chỉ record `DataAccessException`, không record lỗi validation,
  type mapping hoặc application loader;
- bulkhead và circuit breaker bảo vệ cả read/write;
- không fallback Redis sang Caffeine và không thay distributed coordination bằng
  local/JVM fallback.

Startup fail-fast nếu retry có `max-attempts` ngoài khoảng 1..3, wait duration
không nằm trong 1ms..5s, hoặc bulkhead có `max-wait-duration` khác 0. Lỗi auth,
ACL và cấu hình không fail-open; chỉ lỗi connection/transient hoặc circuit-open
được phép đi tới source loader.

Mỗi retry attempt đi riêng qua circuit breaker và bulkhead; permit không bị giữ
trong thời gian retry backoff. `getOrLoad` fail-open sang source loader khi cache
bị lỗi connection hoặc circuit-open, nhưng bulkhead-full vẫn fail-fast để không
dồn tải sang source. Loader có per-key single-flight trong một JVM; kết quả được
publish bằng `putIfAbsent` và cache write là best-effort. Đây không phải distributed
single-flight hay linearizable guard giữa load và concurrent eviction. Registry
single-flight được hard-bound ở 1.024 source loads; vượt ngưỡng sẽ fail-fast.

Application có thể override hai customizer bean tên
`platformCacheRetryConfigCustomizer` và
`platformCacheCircuitBreakerConfigCustomizer`. Timeout/network/pool vẫn thuộc
`spring.data.redis.*`; synchronous cache operation không tạo thread-pool timeout
thứ hai.

## 10. Observability

Khi application có Spring Boot Actuator/Micrometer, module tạo observation
`platform.cache.operation` với low-cardinality tags:

- `cache.operation`: `get`, `put`, `put_if_absent`, `exists`,
  `evict`, `clear`;
- `cache.provider`: `REDIS` hoặc `CAFFEINE`.

Không tag cache key/value, credential, host, exception message hoặc class name.
Application có thể cung cấp `CacheOperationObservability` bean riêng để back off.
Source loader của `getOrLoad` nằm ngoài cache observation để latency/error của
database hoặc API downstream không bị gắn sai vào cache.

Cache hit/miss/size, Redis health, Lettuce observation và Resilience4j metrics/
health tiếp tục dùng native auto-configuration:

```yaml
spring:
  cache:
    redis:
      enable-statistics: true
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=10m,recordStats

management:
  health:
    redis:
      enabled: true
  endpoints:
    web:
      exposure:
        include: health,metrics,caches,circuitbreakers,circuitbreakerevents,retries,retryevents,bulkheads,bulkheadevents
```

Không có `platform.cache.resilience.*` hoặc
`platform.cache.observability.*`; toàn bộ tuning/export/exposure thuộc native
`resilience4j.*`, `spring.cache.*` và `management.*`.

## 11. Native properties rule

Không thêm alias dưới `platform.cache` cho TTL, cache names, null values,
Caffeine spec, Redis host/port/password, timeout, pool, SSL, Sentinel hoặc Cluster.
Tra cứu metadata của Spring Boot/Caffeine cho các tùy chọn này.

## 12. Jasypt ownership

Jasypt starter, supported `PropertyCryptoService` và Environment decryption thuộc
`platform-core`. Cache không chứa Jasypt dependency hay decryptor; Redis secret
được Boot bind sau khi core-owned Environment integration resolve `ENC(...)`.
FQCN crypto cũ của cache chỉ còn deprecated compatibility stubs và không tạo bean.

## 13. ENC(...) usage

```yaml
spring:
  data:
    redis:
      password: ENC(ciphertext)
```

Không log ciphertext, plaintext, decrypted value hoặc input khi crypto thất bại.

## 14. Environment variable master key

Inject `JASYPT_ENCRYPTOR_PASSWORD` từ environment, Kubernetes Secret, Vault,
Secret Manager hoặc CI/CD secret. Không commit giá trị thật vào YAML/script/source.

## 15. Security recommendations

- Giới hạn quyền đọc master key và rotate định kỳ.
- Không expose key/decrypted secret qua actuator hoặc exception.
- Không dùng cache cho credential/source-of-truth/coordination chính xác.
- Không dùng insecure TLS hoặc raw sensitive data làm cache key.

## 16. Migration from old platform.cache.redis.* properties

```yaml
# Old (removed)
platform:
  cache:
    stores:
      primary:
        provider: redis
    caches:
      customers:
        ttl: 10m
```

```yaml
# New
platform:
  cache:
    enabled: true
    provider: redis
spring:
  cache:
    cache-names: [customers]
    redis:
      time-to-live: 10m
  data:
    redis:
      host: localhost
      port: 6379
```

Các cấu hình cũ `platform.cache.defaults`, `stores`, `caches`, `locking`,
serialization, fallback và multi-level đã bị xóa. Chuyển chúng sang native
Spring properties hoặc bean/customizer chính thức của Spring Boot. Resilience và
observability dùng các namespace native mô tả ở trên.
