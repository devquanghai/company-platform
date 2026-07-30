# Caffeine only

```yaml
platform:
  cache:
    application: ${spring.application.name:application}
    stores:
      local-default:
        provider: CAFFEINE
        caffeine:
          maximum-size: 10000
          expire-after-write: 10m
          record-stats: true
    caches:
      local-settings:
        store: local-default
        ttl: 30m
        ttl-jitter:
          enabled: true
          percentage: 10
```

Caffeine chỉ chia sẻ trong cùng JVM. `maximum-size` và TTL là bắt buộc về mặt
vận hành. Không dùng cấu hình này cho coordination hoặc dữ liệu cần nhất quán
giữa nhiều replica.

