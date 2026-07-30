# Redis Cluster

```yaml
platform:
  cache:
    stores:
      redis-cluster:
        provider: REDIS
        redis:
          mode: CLUSTER
          database: 0
          cluster:
            nodes:
              - redis-node-1:6379
              - redis-node-2:6379
              - redis-node-3:6379
            max-redirects: 5
            topology-refresh:
              enabled: true
              adaptive: true
              period: 30s
    caches:
      request-counter:
        store: redis-cluster
        ttl: 5m
        failure-policy: FAIL_CLOSED
        coordination: true
        key:
          prefix: request-counter
          version: v1
          hash-tag: request-counter
```

Database phải bằng `0`. Mọi key tham gia một atomic/Lua operation phải dùng
cùng hash tag và cùng Cluster slot.

