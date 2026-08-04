# Platform integration test applications

Hai ứng dụng được tách riêng để mỗi application context chỉ khởi tạo hạ tầng cần test:

- `platform-cache-test-app` (`18081`): Caffeine, Redis và cache ba tầng `Caffeine L1 -> Caffeine L2 -> Redis L3`.
- `platform-kafka-test-app` (`18082`): publish/consume Kafka qua API của `platform-queue`.

Mặc định cả hai app chỉ bind `127.0.0.1` vì controller không có authentication và probe trả payload test nguyên bản. Chỉ đặt `TEST_APP_BIND_ADDRESS=0.0.0.0` trong mạng test đã được cô lập.

`platform-cache` native hiện hỗ trợ hai tầng `Caffeine L1 -> Redis L2`. Luồng ba tầng trong app này là orchestration ở application layer từ ba named cache độc lập, dùng để quan sát lookup/backfill thủ công; không phải distributed/atomic multi-level implementation của library.

## Build

```bash
JAVA_HOME='/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home' \
  ./mvnw -pl :platform-cache-test-app,:platform-kafka-test-app -am clean package
```

## Cache application

Khởi động Redis local trước. Có thể dùng Redis master trong file hạ tầng hiện có:

```bash
docker compose -f platform-cache/infrastructure/redis-sentinel-docker-compose.yaml up -d redis-master
export CACHE_REDIS_PASSWORD='<local-password>'
java -jar platform-integration-test/apps/cache-test-app/target/platform-cache-test-app-1.0.0-SNAPSHOT.jar
```

Các API chính:

```bash
curl -X PUT http://localhost:18081/api/cache/caffeine/customer-1 \
  -H 'Content-Type: application/json' -d '{"value":"caffeine-value"}'
curl http://localhost:18081/api/cache/caffeine/customer-1

curl -X PUT http://localhost:18081/api/cache/redis/customer-1 \
  -H 'Content-Type: application/json' -d '{"value":"redis-value"}'
curl http://localhost:18081/api/cache/redis/customer-1

curl -X PUT http://localhost:18081/api/cache/three-level/customer-1 \
  -H 'Content-Type: application/json' -d '{"value":"three-level-value"}'
curl -X DELETE http://localhost:18081/api/cache/three-level/L1/customer-1
curl http://localhost:18081/api/cache/three-level/customer-1
curl -X DELETE http://localhost:18081/api/cache/three-level/L1/customer-1
curl -X DELETE http://localhost:18081/api/cache/three-level/L2/customer-1
curl http://localhost:18081/api/cache/three-level/customer-1
```

Hai lần đọc cuối lần lượt trả `source=L2_CAFFEINE` và `source=L3_REDIS`, đồng thời trường `promotedTo` cho biết tầng nào đã được backfill.

## Kafka application

Khởi động Kafka KRaft cluster hiện có, sau đó chạy app:

```bash
docker compose -f platform-queue/infrastructure/kafka-docker-compose.yaml up -d
java -jar platform-integration-test/apps/kafka-test-app/target/platform-kafka-test-app-1.0.0-SNAPSHOT.jar
```

Publish và kiểm tra consumer đã nhận:

```bash
curl -X POST http://localhost:18082/api/kafka/messages \
  -H 'Content-Type: application/json' \
  -d '{"messageId":"manual-001","aggregateId":"customer-1","message":"hello kafka"}'
curl http://localhost:18082/api/kafka/messages
curl http://localhost:18082/api/kafka/status
```

Khi HTTP outcome không rõ ràng, client nên retry cùng `messageId`; probe vẫn có thể ghi nhận duplicate vì app cố ý không bật inbox. Hai app này là single-instance, happy-path test harness: Caffeine/probe đều nằm trong RAM, cache ba tầng không có distributed invalidation, còn Kafka retry/DLT/outbox/inbox không thuộc phạm vi test này.

Các biến môi trường thường dùng: `CACHE_REDIS_HOST`, `CACHE_REDIS_PORT`, `CACHE_REDIS_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TEST_TOPIC`, `KAFKA_TEST_TOPIC_REPLICATION_FACTOR`, `KAFKA_TEST_CONSUMER_GROUP`. Khi chạy Kafka một broker, đặt `KAFKA_TEST_TOPIC_REPLICATION_FACTOR=1`.
