# Redis deployment modes

Standalone, Sentinel và Cluster được Spring Boot suy ra trực tiếp từ
`spring.data.redis.*`; platform-cache không có enum topology hoặc Redis
connection properties riêng.

- Standalone: `spring.data.redis.host`, `port`, `database`.
- Sentinel: `spring.data.redis.sentinel.master`, `nodes`, credential.
- Cluster: `spring.data.redis.cluster.nodes`, `max-redirects` và
  `spring.data.redis.lettuce.cluster.refresh.*`.
- SSL, timeout, client name và pool: dùng các property native tương ứng dưới
  `spring.data.redis.*` và `spring.data.redis.lettuce.*`.

Logical Redis store mặc định sử dụng bean `redisConnectionFactory` do Boot tạo.
Additional store chỉ tham chiếu một bean application-owned bằng
`platform.cache.stores.<name>.connection-factory-bean`.
