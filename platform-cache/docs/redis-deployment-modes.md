# Redis deployment modes

Mỗi Redis named store chọn đúng một mode. Property của mode khác có thể tồn tại
trong file cấu hình dùng chung nhưng không được dùng để tạo connection.

## Standalone

```yaml
platform:
  cache:
    stores:
      redis-primary:
        provider: REDIS
        redis:
          mode: STANDALONE
          database: 0
          standalone:
            host: ${REDIS_HOST:localhost}
            port: ${REDIS_PORT:6379}
```

Phù hợp local/dev hoặc Redis managed có một endpoint. Production cần TLS,
authentication và HA do nền tảng Redis cung cấp.

## Sentinel

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
            master: mymaster
            nodes:
              - redis-sentinel-1:26379
              - redis-sentinel-2:26379
              - redis-sentinel-3:26379
            username: ${REDIS_SENTINEL_USERNAME:}
            password: ${REDIS_SENTINEL_PASSWORD:}
```

Sentinel credential có thể khác data-node credential. Cấu hình ít nhất các node
độc lập phù hợp với topology thực tế; failover không loại bỏ nhu cầu retry có
giới hạn.

## Cluster

```yaml
platform:
  cache:
    stores:
      redis-primary:
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
```

Cluster chỉ hỗ trợ database `0`. Seed node không cần liệt kê toàn bộ cluster,
nhưng nên thuộc nhiều failure domain. Multi-key/Lua operation phải đặt toàn bộ
key cùng slot bằng hash tag do configuration kiểm soát.

## TLS và SSL bundle

```yaml
redis:
  ssl:
    enabled: true
    verify-peer: true
```

`verify-peer=false` bị validator từ chối. Trust material do JVM/application
quản lý; module không chứa certificate hoặc private key.

## Nhiều store và lifecycle

Mỗi named Redis store có factory/template được qualify theo tên store. Store
disabled không tạo connection. Nếu `connection-factory-bean` được chỉ định,
application/Spring sở hữu lifecycle của factory; registry platform chỉ tham
chiếu và không tự đóng. Resource do platform tạo vẫn do Spring container đóng
đúng một lần.

## Timeout và pool

Luôn đặt connect, command, shutdown, pool max-wait và health timeout hữu hạn.
Pool sizing phải dựa trên concurrency/latency thực đo; tăng pool không sửa được
Redis chậm. Retry nhân tải nên giữ attempt thấp và chỉ áp dụng lỗi transient.
