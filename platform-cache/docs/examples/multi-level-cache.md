# Multi-level cache

```yaml
platform:
  cache:
    stores:
      local-default:
        provider: CAFFEINE
        caffeine:
          maximum-size: 20000
          expire-after-write: 5m
      redis-primary:
        provider: REDIS
        redis:
          mode: STANDALONE
          standalone:
            host: ${REDIS_HOST:localhost}
            port: ${REDIS_PORT:6379}
    caches:
      reference-data:
        ttl: 1h
        multi-level:
          enabled: true
          l1-store: local-default
          l2-store: redis-primary
          l1-ttl: 5m
          l2-ttl: 1h
          populate-l1-on-l2-hit: true
          write-policy: EVICT_L1_THEN_WRITE_L2
```

Multi-level cache route trực tiếp tới `l1-store` và `l2-store`; không cần một
primary `store` mơ hồ. L1 TTL không được lớn hơn L2 TTL.
