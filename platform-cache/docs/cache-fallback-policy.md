# Cache fallback policy

Fallback chỉ là degraded behavior khi Redis primary lỗi hạ tầng. Nó không được
che lỗi validation, serialization, key design hoặc version conflict.

## Failure policy

| Policy | Hành vi |
|---|---|
| `FAIL_OPEN` | Bỏ qua cache failure và cho caller tải source of truth |
| `FAIL_CLOSED` | Trả lỗi ngay; bắt buộc cho coordination |
| `FALLBACK_LOCAL` | Dùng Caffeine fallback đã cấu hình và xác thực |

## Fallback mode

| Mode | Read | Write khi primary lỗi | Rủi ro |
|---|---|---|---|
| `NONE` | Không local fallback | Không | Thấp |
| `READ_ONLY` | Chỉ đọc entry local còn fresh | Không | Miss tăng |
| `READ_THROUGH` | Có thể dùng local và populate từ loader | Mặc định không | Dữ liệu per-instance |
| `STALE_IF_ERROR` | Cho phép stale trong cửa sổ cấu hình | Không | Caller phải nhận biết stale |
| `LOCAL_READ_WRITE` | Đọc/ghi local | Có opt-in | Split-brain |

`STALE_IF_ERROR` lưu `freshUntil` và `staleUntil`; physical local TTL phủ toàn
bộ stale window. Chỉ `CacheResult` được phép trả `HIT_STALE` cùng `stale=true`.
Simple `get` không âm thầm trả stale.

## Recovery

`clear-on-primary-recovery=true` xóa fallback sau khi Redis được xác nhận phục
hồi, tránh local value tiếp tục thắng primary. Không “đồng bộ ngược” toàn bộ
local write về Redis vì thứ tự update giữa các instance không thể chứng minh.

## Cấm fallback

Validator phải từ chối local fallback cho:

- distributed lock và fencing;
- balance, quota và exact counter;
- idempotency key;
- authentication/authorization/security state;
- leader election hoặc distributed coordination.

Những trường hợp trên dùng `coordination=true` và `FAIL_CLOSED`.

## Ví dụ

```yaml
platform:
  cache:
    caches:
      customer-profile:
        store: redis-primary
        failure-policy: FALLBACK_LOCAL
        fallback:
          enabled: true
          type: CAFFEINE
          mode: STALE_IF_ERROR
          local-store: local-default
          ttl: 2m
          maximum-stale: 5m
          clear-on-primary-recovery: true
```
