# Redis Cluster

```yaml
spring:
  data:
    redis:
      cluster:
        nodes: redis-1:6379,redis-2:6379,redis-3:6379
        max-redirects: 5
      lettuce:
        cluster:
          refresh:
            adaptive: true
            period: 30s

platform:
  cache:
    stores:
      redis-cluster:
        provider: REDIS
```
