# Redis Sentinel

```yaml
platform:
  cache:
    stores:
      redis-primary:
        provider: REDIS
        redis:
          mode: SENTINEL
          database: 0
          username: ${REDIS_USERNAME:}
          password: ${REDIS_PASSWORD:}
          sentinel:
            master: ${REDIS_SENTINEL_MASTER:mymaster}
            nodes:
              - ${REDIS_SENTINEL_1:redis-sentinel-1:26379}
              - ${REDIS_SENTINEL_2:redis-sentinel-2:26379}
              - ${REDIS_SENTINEL_3:redis-sentinel-3:26379}
            username: ${REDIS_SENTINEL_USERNAME:}
            password: ${REDIS_SENTINEL_PASSWORD:}
    caches:
      customers:
        store: redis-primary
        ttl: 15m
```

Sentinel credential và Redis data-node credential được cấu hình độc lập.

