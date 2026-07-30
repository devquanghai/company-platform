# platform-cache

`platform-cache` là thư viện cache dùng chung cho các Spring Boot service. Module
cung cấp một API trung lập provider, named store/cache, Caffeine, Redis,
multi-level L1/L2, Spring Cache bridge và các capability consistency,
resilience, observability. Cache luôn là lớp tăng tốc có thể tái tạo, **không
phải source of truth**.

## Bắt đầu nhanh

Module kế thừa dependency management của repository; application chỉ cần thêm
dependency `com.company.platform:platform-cache` và khai báo tối thiểu:

```yaml
platform:
  cache:
    application: ${spring.application.name}
    stores:
      local-default:
        provider: CAFFEINE
        caffeine:
          maximum-size: 10000
          expire-after-write: 10m
    caches:
      customer-profile:
        store: local-default
        ttl: 10m
```

Không tạo application entry point, không component scan và không chứa
credential. Auto-configuration được đăng ký bằng `AutoConfiguration.imports`;
mọi bean mặc định phải back off khi application cung cấp bean tương đương.

## Kiến trúc

```text
consumer
  └─ api (facade, typed, atomic, optimistic, lock)
       └─ application (resolver, policy, ports)
            ├─ adapter/caffeine
            ├─ adapter/redis
            ├─ adapter/multilevel
            ├─ adapter/noop
            └─ adapter/springcache
                 └─ resilience / consistency / observability
                      └─ autoconfigure
```

Package `api` và `domain` không lộ type của Caffeine, Lettuce, Spring Data Redis
hoặc Redisson. `autoconfigure` chỉ validate và wiring; hành vi nằm trong
application service/adapter. Thiết kế chi tiết ở
[platform-cache-design.md](docs/platform-cache-design.md).

## Named store và named cache

- **Store** sở hữu tài nguyên hạ tầng: `local-default`, `redis-primary`.
- **Cache** sở hữu policy kỹ thuật và tham chiếu store: `customers`,
  `permissions`.
- Store sai tên, disabled store, route provider không tương thích hoặc cấu hình
  mơ hồ làm ứng dụng fail fast trước khi mở connection.
- `NOOP` luôn miss và không lưu dữ liệu; hữu ích để tắt cache có chủ đích.
- `MULTI_LEVEL` là route cấp cache, không phải provider hạ tầng của store.

## Caffeine

Caffeine là local cache riêng từng JVM. Mỗi cache phải được giới hạn bằng
`maximum-size` và TTL. Local atomic operation chỉ bảo đảm atomic trong một JVM.
Không dùng Caffeine cho quota, idempotency, security state, balance hoặc
distributed coordination.

Xem [caffeine-only.md](docs/examples/caffeine-only.md).

## Redis

Redis store dùng JSON envelope an toàn, timeout hữu hạn, pool và resilience.
Module hỗ trợ:

- `STANDALONE`: một endpoint.
- `SENTINEL`: master group và danh sách Sentinel node.
- `CLUSTER`: seed nodes, redirect và topology refresh; database bắt buộc bằng
  `0`.

Chỉ nhánh deployment mode được chọn được dùng để tạo connection. Có thể khai
báo nhiều Redis store hoặc tham chiếu `connection-factory-bean` do application
quản lý. Chi tiết ở
[redis-deployment-modes.md](docs/redis-deployment-modes.md).

## Multi-level L1 + L2

L1 phải là Caffeine và L2 phải là Redis. Read đi L1 rồi L2; L2 hit chỉ populate
L1 nếu namespace token và entry invalidation epoch không đổi. Mutation
invalidate L1 trước, mutate L2 rồi mới phát invalidation. Nếu L2 mutation lỗi,
key chuyển sang trạng thái `DIRTY_DO_NOT_POPULATE` tại instance hiện tại để
không nạp lại value cũ.

L1 TTL không vượt quá freshness còn lại của L2. Invalidation đa instance là
eventual và vẫn bị chặn bởi L1 TTL. Xem
[cache-consistency-model.md](docs/cache-consistency-model.md).

## Facade API

```java
@Service
@RequiredArgsConstructor
public class CustomerQueryService {
    private final PlatformCacheOperations cache;
    private final CustomerRepository repository;

    public CustomerResponse find(String customerId) {
        return cache.getOrLoad(
            "customer-profile",
            customerId,
            CustomerResponse.class,
            () -> repository.findById(customerId)
                .map(CustomerResponse::from)
                .orElseThrow(CustomerNotFoundException::new)
        );
    }
}
```

`getResult` trả trạng thái chi tiết như hit/miss/stale/degraded mà không làm
`Optional#get` âm thầm trả stale.

## Typed cache

```java
@Bean
TypedCacheOperations<String, CustomerResponse> customerCache(
        TypedCacheFactory factory) {
    return factory.getCache(
        "customer-profile", String.class, CustomerResponse.class);
}
```

Typed facade cố định tên cache và type, giảm lặp chuỗi/type ở business service.

## Spring Cache annotation

Khi `annotations-enabled=true`, named cache được expose qua Spring Cache:

```java
@Cacheable(cacheNames = "customer-profile", key = "#customerId", sync = true)
public CustomerResponse find(String customerId) {
    return loadFromDatabase(customerId);
}
```

Spring Cache API không biểu diễn đầy đủ stale/degraded status; dùng
`PlatformCacheOperations#getResult` khi caller cần phân biệt các trạng thái đó.

## TTL, negative cache và jitter

- Named cache kế thừa `defaults.ttl` nếu không khai báo TTL riêng.
- Negative cache dùng marker có schema và TTL ngắn hơn TTL bình thường; không
  biến mọi lỗi loader thành “không tồn tại”.
- TTL jitter phân tán thời điểm hết hạn, validator giới hạn `0..50%`.
- Logical clear thay `cacheNamespaceToken` 128-bit thay vì dùng Redis `KEYS`.
  Kết quả clear không bịa số entry đã xóa.

## Stampede protection

`SINGLE_FLIGHT` gộp loader cùng identity trong một JVM. Identity gồm store,
cache, namespace token, entry invalidation epoch và encoded key. Follower
timeout không hủy leader, không xóa future của leader và không khởi chạy loader
thứ hai. Loader failure được chia sẻ nhưng không được cache.

## Resilience

Redis pipeline áp dụng bulkhead, circuit breaker rồi retry có giới hạn. Chỉ lỗi
kết nối tạm thời được retry; validation, serialization và version conflict
không phải infrastructure failure. Timeout là hữu hạn ở connection, command,
pool và health.

`FAIL_OPEN` cho phép tải source of truth, `FAIL_CLOSED` trả lỗi và
`FALLBACK_LOCAL` áp dụng policy local đã xác thực. Xem
[cache-fallback-policy.md](docs/cache-fallback-policy.md).

## Fallback, degraded mode và stale data

Fallback Caffeine hỗ trợ `NONE`, `READ_ONLY`, `READ_THROUGH`,
`STALE_IF_ERROR`, `LOCAL_READ_WRITE`. `STALE_IF_ERROR` chỉ mở khi primary lỗi
hạ tầng, theo `freshUntil/staleUntil`, và chỉ được công bố qua `CacheResult`
với `stale=true`. Coordination cache, distributed lock, exact counter và
security state không được fallback local.

`LOCAL_READ_WRITE` có nguy cơ split-brain nên cần opt-in
`allow-local-write-fallback=true` và không phù hợp cho dữ liệu cần consistency
đa instance.

## Atomic operation

```java
long current = atomicCacheOperations.increment(
    "request-counter", customerId, 1);

boolean changed = atomicCacheOperations.compareAndSet(
    "customer-preference", customerId, expected, replacement);
```

Redis increment dùng numeric representation riêng. CAS so sánh canonical
payload/schema marker, không so sánh timestamp envelope mới tạo. Lua script
nhận toàn bộ key qua `KEYS` và phải giữ cùng Redis Cluster slot.

## Optimistic locking

```java
VersionedValue<CustomerPreference> current =
    optimisticCacheOperations.getVersioned(
        "customer-preference", customerId, CustomerPreference.class);

OptimisticUpdateResult<CustomerPreference> result =
    optimisticCacheOperations.updateIfVersion(
        "customer-preference", customerId,
        current.getVersion(), updatedPreference);
```

`entryVersion` tách khỏi namespace token/invalidation epoch. Updater trong
`computeWithRetry` phải side-effect-free vì có thể chạy nhiều lần. Optimistic
cache không thay thế database transaction hoặc JPA `@Version`.

## Distributed locking

```java
PaymentResult result = distributedLockOperations.executeWithLock(
    "payment:" + paymentId,
    LockOptions.builder()
        .waitTime(Duration.ofSeconds(2))
        .leaseTime(Duration.ofSeconds(30))
        .build(),
    () -> paymentProcessor.process(paymentId)
);
```

Lock SPI luôn fail-closed: timeout, lease loss, owner loss hoặc circuit open đều
không chạy protected action. Không có custom Redlock và không fallback sang
JVM lock. Fencing mode chỉ an toàn nếu tài nguyên được bảo vệ xác minh fencing
token. Database transaction và idempotency vẫn bắt buộc. Xem
[distributed-locking.md](docs/distributed-locking.md).

## Serialization

Redis value dùng shared `JsonMapperHelper` của `platform-core` và versioned JSON
envelope. Java native serialization, default typing không allowlist và raw
`Object#toString` bị cấm. Khi thay schema, tăng `schema-version`/`schema-id`
hoặc key version và triển khai migration có kiểm soát. Xem
[cache-serialization.md](docs/cache-serialization.md).

## Key design và Redis Cluster hash slot

Key vật lý chứa application/environment, cache prefix/version, namespace token
và encoded user key. Sensitive key dùng SHA-256; không dùng `hashCode()`. Không
đưa raw key/value vào log, metric hoặc exception. Hash tag phải cố định từ
configuration, không cho user input chèn `{}`. Mọi key của một Lua script phải
cùng slot. Xem [cache-key-design.md](docs/cache-key-design.md).

## Metrics, tracing, events và health

Observability dùng tag cardinality thấp như store/cache/provider/outcome; không
dùng raw key. Metric cần theo dõi gồm hit/miss/load, operation latency, eviction,
fallback/stale, circuit state, lock acquisition và serialization failure.
Structured event chỉ chứa metadata đã sanitize và trace/request context nếu có.

Health contributor kiểm tra store đang bật với `health-timeout`, không tạo
connection cho disabled store và không làm lộ endpoint/credential. Cache health
không đồng nghĩa source of truth bị lỗi.

## Security checklist

- Credential chỉ đến từ secret/environment; không commit hoặc log.
- TLS peer verification bật mặc định.
- Không Java serialization, `KEYS *`, raw PII key/value hoặc `hashCode()`.
- Caffeine luôn bounded; payload bị giới hạn bởi `maximum-entry-size`.
- Distributed coordination/lock luôn fail-closed.
- Retry chỉ dành cho lỗi transient và không retry toàn critical section.

## Troubleshooting

| Triệu chứng | Kiểm tra |
|---|---|
| Startup fail vì unknown store | `caches.<name>.store` phải trỏ enabled store |
| Cluster báo cross-slot | Dùng hash tag cố định và bảo đảm mọi Lua key cùng slot |
| Hit ratio L1 thấp | Kiểm tra L1 TTL, invalidation epoch và entry size |
| Redis recovery nhưng vẫn local | Kiểm tra circuit state và `clear-on-primary-recovery` |
| Không thấy metric/health | Kiểm tra classpath, bean back-off và observability flags |
| Deserialize lỗi sau deploy | Kiểm tra schema ID/version và compatibility |
| `clear()` không trả số xóa | Đây là logical namespace clear; số chính xác không tồn tại |

## Migration guide

1. Inventory cache hiện tại, xác định source of truth và consistency class.
2. Tách hạ tầng thành named store, policy thành named cache.
3. Thay raw Redis/Caffeine access bằng facade hoặc typed cache.
4. Chọn key prefix/version; không tái sử dụng key Java serialization cũ.
5. Với Redis, rollout namespace/schema mới trước rồi mới dừng writer cũ.
6. Chỉ bật fallback sau khi chứng minh dữ liệu không dùng cho coordination.
7. Chuyển annotation Spring Cache sau khi cache name/TTL đã được validation.
8. Theo dõi hit/miss/error/stale và rollback bằng cách disable named cache hoặc
   chuyển sang NOOP có chủ đích.

## Tài liệu và cấu hình mẫu

- [Consistency model](docs/cache-consistency-model.md)
- [Redis deployment modes](docs/redis-deployment-modes.md)
- [Fallback policy](docs/cache-fallback-policy.md)
- [Key design](docs/cache-key-design.md)
- [Serialization](docs/cache-serialization.md)
- [Distributed locking](docs/distributed-locking.md)
- [Cấu hình đầy đủ](docs/examples/application-cache.yml)

