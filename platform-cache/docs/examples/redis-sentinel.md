# Redis Sentinel

```yaml
spring:
  data:
    redis:
      password: ${REDIS_PASSWORD}
      sentinel:
        master: mymaster
        nodes: sentinel-1:26379,sentinel-2:26379,sentinel-3:26379
        username: ${REDIS_SENTINEL_USERNAME:}
        password: ${REDIS_SENTINEL_PASSWORD:}

platform:
  cache:
    stores:
      redis-primary:
        provider: REDIS
```
