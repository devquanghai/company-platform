# Redis Standalone

```yaml
platform:
  cache:
    stores:
      redis-primary:
        provider: REDIS
        redis:
          mode: STANDALONE
          database: 0
          username: ${REDIS_USERNAME:}
          password: ${REDIS_PASSWORD:}
          standalone:
            host: ${REDIS_HOST:localhost}
            port: ${REDIS_PORT:6379}
          serialization:
            key: STRING
            value: JSON
            value-envelope-enabled: true
    caches:
      products:
        store: redis-primary
        ttl: 10m
```

Không commit credential. Với production managed Redis, bật TLS và peer
verification theo SSL bundle của application.

