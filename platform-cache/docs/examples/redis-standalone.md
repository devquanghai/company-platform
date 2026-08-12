# Redis standalone

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      username: ${REDIS_USERNAME:}
      password: ${REDIS_PASSWORD:}
      database: 0
      connect-timeout: 2s
      timeout: 2s

platform:
  cache:
    stores:
      redis-primary:
        provider: REDIS
    caches:
      customer-profile:
        store: redis-primary
        ttl: 10m
```
