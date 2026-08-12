# Multi-level cache

```yaml
spring:
  cache:
    caffeine:
      spec: maximumSize=5000,recordStats
  data:
    redis:
      host: localhost
      port: 6379

platform:
  cache:
    stores:
      local:
        provider: CAFFEINE
      distributed:
        provider: REDIS
    caches:
      customer-profile:
        store: distributed
        ttl: 10m
        multi-level:
          enabled: true
          l1-store: local
          l2-store: distributed
          l1-ttl: 30s
```
