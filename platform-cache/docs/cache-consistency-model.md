# Cache consistency model

## Phạm vi bảo đảm

Cache là dữ liệu dẫn xuất và có thể mất bất kỳ lúc nào. Source of truth phải tự
bảo đảm transaction, idempotency và optimistic/pessimistic concurrency. Module
phân biệt rõ:

- Caffeine: atomic trong một JVM.
- Redis: atomic theo một key hoặc một nhóm key cùng Cluster slot.
- Multi-level: eventual consistency giữa các instance, bị chặn bởi L1 TTL.
- Distributed lock: coordination fail-closed, không biến cache thành database.

## Ba loại version độc lập

| Token | Phạm vi | Thay đổi khi | Mục đích |
|---|---|---|---|
| `cacheNamespaceToken` | Toàn named cache | logical clear | Đổi namespace mà không scan key |
| `entryInvalidationEpoch` | Một entry/JVM | put/evict/invalidation | Chặn refill cũ sau mutation |
| `entryVersion` | Một entry | optimistic commit | Compare/update theo version |

Namespace token là giá trị opaque 128-bit, không tuyên bố đơn điệu. Mutation key
A không được làm key B miss; vì vậy entry epoch không được tái sử dụng làm
namespace toàn cache.

## Read và refill guard

Reader snapshot namespace token và entry epoch trước khi gọi L2/loader. Value chỉ
được populate L1 khi hai snapshot vẫn giữ nguyên. Nếu follower single-flight ở
epoch mới, nó không join hoặc nhận future của epoch cũ.

L1 TTL được tính bằng giá trị nhỏ nhất giữa TTL cấu hình và freshness còn lại
của L2 envelope.

## Mutation multi-level

Thứ tự mặc định:

1. Invalidate L1 local.
2. Tăng entry invalidation epoch.
3. Mutate L2.
4. Publish invalidation.
5. Có thể populate L1 bằng committed value.

Nếu bước 3 lỗi, entry local chuyển `DIRTY_DO_NOT_POPULATE`. Instance biết L2 có
thể cũ sẽ không đọc/refill value đó cho tới khi một mutation L2 thành công hoặc
invalidation mới xác nhận trạng thái mới. Sự cố liên instance vẫn chỉ eventual
vì outage Redis không thể phát invalidation đáng tin cậy.

## Clear

`clear()` atomically thay namespace token, không gọi `KEYS`, không scan toàn
database và không bịa exact deleted count. Key namespace cũ hết hạn tự nhiên.
Metadata namespace phải non-expiring và được bảo vệ khỏi eviction. Redis
flush/restore có thể làm mất metadata; xác suất trùng token 128-bit rất thấp
nhưng module không tuyên bố absolute non-reuse.

## Atomic và optimistic

- Redis increment/decrement dùng numeric representation riêng.
- CAS/compare-delete so sánh canonical payload cùng schema/negative marker,
  không so sánh timestamp envelope mới serialize.
- Lua script nhận key qua `KEYS`; tất cả key phải cùng hash slot.
- Optimistic update dùng `entryVersion` và bounded retry.
- Updater có thể chạy lại nên phải side-effect-free.
- Version conflict là kết quả nghiệp vụ của cache operation, không phải lỗi hạ
  tầng và không làm circuit breaker tăng failure.

## Single-flight

Identity gồm store, cache, namespace, entry epoch và encoded key. Leader luôn tự
hoàn tất future; follower timeout không cancel leader, không xóa future, không
chạy loader thứ hai. Failure/cancellation chỉ remove đúng future cùng identity.
Interruption phải khôi phục interrupt flag.

## Dữ liệu không được fallback local

Không dùng local fallback cho distributed lock, balance, quota, idempotency,
security state, exact counter hoặc bất kỳ dữ liệu coordination nào. Các cache
này phải `coordination=true` và `failure-policy=FAIL_CLOSED`.

