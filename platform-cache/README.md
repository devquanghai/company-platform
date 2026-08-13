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

## 9. Native properties rule

Không thêm alias dưới `platform.cache` cho TTL, cache names, null values,
Caffeine spec, Redis host/port/password, timeout, pool, SSL, Sentinel hoặc Cluster.
Tra cứu metadata của Spring Boot/Caffeine cho các tùy chọn này.

## 10. Jasypt encryption

Starter Jasypt xử lý `ENC(...)` ở Environment/PropertySource trước khi Boot bind
properties. Cơ chế áp dụng cho Redis, datasource, Kafka, mail và mọi property khác;
platform không tự decrypt Redis password.

```yaml
jasypt:
  encryptor:
    password: ${JASYPT_ENCRYPTOR_PASSWORD}
```

Chỉ khai báo master password khi sử dụng Jasypt. Thiếu/sai key làm resolution/bind
của property `ENC(...)` thất bại; cấu hình secret được Boot bind khi startup vì thế
fail-fast. Property mã hóa chưa từng được resolve vẫn giữ semantics lazy của Jasypt.
Không có fallback về ciphertext thô.

## 11. Encrypt value

Java API:

```java
String encrypted = propertyCryptoService.encryptAndWrap("redis-password");
```

Maven plugin của Jasypt:

```bash
mvn jasypt:encrypt-value \
  -Djasypt.encryptor.password="${JASYPT_ENCRYPTOR_PASSWORD}" \
  -Djasypt.plugin.value="my-secret"
```

`encryptAndWrap` trả nguyên input đã bọc `ENC(...)`, tránh double encryption.

## 12. Decrypt value

`PropertyCryptoService.decrypt` nhận cả ciphertext thuần và `ENC(ciphertext)`.
Cho tác vụ operator cục bộ:

```bash
mvn jasypt:decrypt-value \
  -Djasypt.encryptor.password="${JASYPT_ENCRYPTOR_PASSWORD}" \
  -Djasypt.plugin.value="<encrypted-value>"
```

Không expose encrypt/decrypt qua REST endpoint.

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
`observability`, serialization, fallback và multi-level đã bị xóa. Chuyển chúng
sang native Spring properties hoặc bean/customizer chính thức của Spring Boot.
