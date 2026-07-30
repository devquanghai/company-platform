# Cache key design

## Mục tiêu

Key phải deterministic, bounded, không lộ PII, hỗ trợ schema migration và tương
thích Redis Cluster. Không dùng Java `hashCode()` vì collision và không ổn định
giữa representation.

Một key logic gồm:

```text
application : environment : cache-prefix : key-version
            : cache-namespace-token : encoded-business-key
```

Delimiter phải được escape/length-encode để dữ liệu người dùng không tạo key
mơ hồ.

## Sensitive key

Khi `key.sensitive=true`, phần business key dùng SHA-256. Digest phục vụ
determinism, không phải password storage. Không log raw key; observability chỉ
được `HASH` hoặc `OMIT`. Value cũng không được đưa vào metric/tag.

## Version và migration

Tăng `key.version` khi thay representation không backward-compatible. Rollout
reader/writer namespace mới, giữ TTL để namespace cũ tự hết hạn rồi mới loại bỏ
compatibility code. Logical clear thay namespace token thay vì scan/xóa key.

## Redis Cluster hash tag

Redis dùng nội dung trong `{...}` để chọn slot. Atomic multi-key/Lua operation
chỉ hợp lệ nếu mọi key cùng slot:

```text
orders:{customer-profile}:v1:<namespace>:<key>
orders:{customer-profile}:v1:<namespace>:<key>:version
```

Hash tag lấy từ configuration allowlist, không lấy từ user input. Encoder phải
từ chối `{` và `}` trong phần key người dùng để tránh slot injection.

Không đặt mọi cache vào một hash tag toàn cục vì sẽ tạo hot slot.

## Clear an toàn

Không gọi `KEYS`, không `SCAN` toàn shared database. `clear()` atomically sinh
namespace token 128-bit mới; entry namespace cũ hết hạn theo TTL. Kết quả clear
trả token/trạng thái, không giả định biết chính xác số key đã xóa.

