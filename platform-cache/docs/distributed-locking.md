# Distributed locking

`DistributedLockOperations` là capability riêng, không phải side effect của
cache access. Adapter tùy chọn có thể dùng Redisson; module không tự xây Redlock.

## Contract an toàn

- Acquisition timeout hoặc circuit open: fail-closed.
- Không chạy protected action nếu chưa xác nhận ownership.
- Lease/watchdog mất ownership: fail-closed và phát event đã sanitize.
- Không fallback sang `synchronized`, `ReentrantLock` hoặc Caffeine.
- Không retry toàn critical section vì action có thể đã gây side effect.
- Lock name không chứa raw PII/credential.

## Fencing

Watchdog không ngăn paused process tiếp tục ghi sau khi lease đã được owner mới
lấy. Với tài nguyên quan trọng, bật fencing và yêu cầu tài nguyên đích từ chối
token thấp hơn token đã thấy. Nếu database/API đích không kiểm tra fencing
token, việc bật property một mình không tạo ra bảo đảm.

## Ví dụ

```java
PaymentResult result = distributedLockOperations.executeWithLock(
    "payment:" + paymentId,
    LockOptions.builder()
        .waitTime(Duration.ofSeconds(2))
        .leaseTime(Duration.ofSeconds(30))
        .watchdogEnabled(true)
        .fencingEnabled(true)
        .build(),
    () -> paymentProcessor.process(paymentId)
);
```

Protected action vẫn cần database transaction và idempotency key. Lock không
đảm bảo exactly-once khi process/network thất bại.

## Cấu hình

```yaml
platform:
  cache:
    locking:
      enabled: true
      provider: REDISSON
      wait-time: 2s
      lease-time: 30s
      watchdog-enabled: true
      fencing-enabled: true
```

Nếu provider/dependency/connection bắt buộc không tồn tại, startup phải fail rõ
ràng; không đăng ký implementation giả thành công.

