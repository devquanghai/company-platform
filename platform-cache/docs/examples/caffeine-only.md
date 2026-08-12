# Caffeine only

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=10000,recordStats

platform:
  cache:
    stores:
      local:
        provider: CAFFEINE
    caches:
      customer-profile:
        store: local
        ttl: 10m
```

Capacity/options thuộc Caffeine native spec; named TTL là policy orchestration
của platform.
