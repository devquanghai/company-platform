# Platform Queue

## 1. Overview

`platform-queue` chọn một provider và cung cấp `MessagePublisher` trung lập.
Kafka/RabbitMQ connection, producer, consumer, listener, security, topology,
retry và transaction vẫn do Spring Boot và broker library sở hữu.

## 2. Architecture

```text
platform.queue.provider
        |
        +-- KAFKA ----> Boot KafkaAutoConfiguration ----> KafkaTemplate
        |
        +-- RABBITMQ -> Boot RabbitAutoConfiguration ---> RabbitTemplate
                                                        |
                                                        v
                                                MessagePublisher
```

Platform không tạo producer/consumer factory, template, connection factory,
admin hay listener container factory thứ hai.

## 3. Enable / disable

```yaml
platform:
  queue:
    enabled: true
    provider: kafka
```

`enabled=false` chỉ tắt bean thuộc platform queue; bean native của application
không bị mutate. Provider mặc định là `KAFKA`. Chọn provider thiếu dependency
tương ứng sẽ làm startup fail rõ ràng; không có broker fallback.

## 4. Kafka provider

Application thêm `spring-boot-kafka` và cấu hình trực tiếp `spring.kafka.*`.
`PublishRequest.destination` là physical topic. Platform dùng đúng
`KafkaTemplate` do Boot tạo và back off khi application cung cấp
`MessagePublisher` riêng.

## 5. RabbitMQ provider

Application thêm `spring-boot-amqp` và cấu hình `spring.rabbitmq.*`.
`publish(destination, payload)` gửi tới queue qua default exchange;
`publish(destination, key, payload)` coi destination là exchange và key là
routing key.

## 6. Native Spring Boot property rule

`platform.queue` chỉ hỗ trợ `enabled` và `provider`. Legacy keys như `brokers`,
`destinations`, `subscriptions`, `topology`, `delivery`, `observability`,
`kafka` hoặc `rabbitmq` bị reject khi startup. Không có alias hoặc property copy.

## 7. Kafka producer configuration

```yaml
spring:
  kafka:
    bootstrap-servers: [kafka-01:9092, kafka-02:9092, kafka-03:9092]
    client-id: ${spring.application.name}
    producer:
      acks: all
      retries: 10
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
        max.request.size: 1048576
```

## 8. Kafka consumer configuration

```yaml
spring:
  kafka:
    consumer:
      group-id: ${spring.application.name}
      enable-auto-commit: false
      auto-offset-reset: earliest
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: com.company.loan.messaging
        spring.json.use.type.headers: false
        spring.json.value.default.type: com.company.loan.messaging.LoanEvent
    listener:
      concurrency: 3
      observation-enabled: true
    template:
      observation-enabled: true
```

Applications đăng ký consumer bằng native `@KafkaListener`.
Chỉ allow-list package payload cụ thể; không dùng `*` hoặc wildcard rộng trong
môi trường production.

## 9. Kafka manual commit

```yaml
spring:
  kafka:
    listener:
      ack-mode: manual_immediate
```

Listener nhận `Acknowledgment` và chỉ acknowledge sau khi business transaction
hoàn tất. At-least-once vẫn tạo duplicate window; dùng durable inbox khi cần.

## 10. Retry strategy

Kafka producer retry dùng `spring.kafka.producer.retries`. Processing retry dùng
Spring Kafka `DefaultErrorHandler`/`@RetryableTopic`. Rabbit listener retry dùng
`spring.rabbitmq.listener.*.retry.*` hoặc official listener customizer. Không có
platform retry loop và không retry publish có outcome không xác định một cách mù.

## 11. DLQ strategy

Kafka dùng `DeadLetterPublishingRecoverer`; RabbitMQ dùng DLX/DLQ hoặc
`MessageRecoverer`. Application sở hữu topic/queue naming và topology. Original
message chỉ được ACK/commit sau khi retry/DLQ boundary tương ứng thành công.

## 12. Idempotency

Kafka producer idempotence là native client property, không thay thế consumer
idempotency. Application triển khai durable inbox/outbox tại transaction boundary
của business database khi cần; module không cung cấp store abstraction hoặc
fallback in-memory.

## 13. Kafka transactions

Dùng native transaction id prefix duy nhất cho từng application instance,
transaction-aware beans của Spring Kafka và consumer
`isolation.level=read_committed` khi cần ẩn aborted records.
Platform không tạo transaction manager thứ hai. Kafka transaction không cung
cấp exactly-once cho side effect ở database khác; dùng outbox/inbox phù hợp.

## 14. RabbitMQ configuration

```yaml
platform:
  queue:
    enabled: true
    provider: rabbitmq
spring:
  rabbitmq:
    host: rabbitmq
    port: 5671
    username: ${RABBITMQ_USERNAME}
    password: ENC(...)
    virtual-host: /
    connection-timeout: 3s
    ssl:
      enabled: true
      trust-store: ${RABBITMQ_TRUSTSTORE_LOCATION}
      trust-store-password: ENC(...)
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true
      observation-enabled: true
    listener:
      simple:
        observation-enabled: true
        acknowledge-mode: manual
        concurrency: 3
        max-concurrency: 10
        prefetch: 50
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 3
          initial-interval: 500ms
          multiplier: 2
          max-interval: 5s
```

Applications đăng ký consumer bằng native `@RabbitListener`.
Application phải cung cấp JSON/bytes `MessageConverter`; platform fail startup
nếu `RabbitTemplate` còn dùng `SimpleMessageConverter` có Java serialization.
Ví dụ Boot customization:

```java
@Bean
JacksonJsonMessageConverter rabbitJsonMessageConverter(JsonMapper jsonMapper) {
    return new JacksonJsonMessageConverter(jsonMapper);
}
```

Khi retry hết, `default-requeue-rejected=false` phải kết hợp DLX/DLQ hoặc
`MessageRecoverer`; DLQ/parking-lot là terminal và không tự requeue. Chỉ ACK sau
business/recovery boundary thành công.

Topology thuộc application: khai báo durable `Queue`, `Exchange`, `Binding`
beans; chọn rõ classic/quorum/stream, routing và DLX arguments. Không
delete/recreate topology khi mismatch; để Rabbit admin fail startup và migrate
topology có kiểm soát.

## 15. Observability / tracing

Dùng Spring Kafka/Rabbit observation, Micrometer và Actuator native. Bật
`spring.kafka.template.observation-enabled`,
`spring.kafka.listener.observation-enabled`,
`spring.rabbitmq.template.observation-enabled` và observation của listener
container đang dùng. Module
không tạo fake health indicator, duplicate timer hay tracing SDK. Kafka/Rabbit
client metrics và Rabbit health được Boot bind khi Actuator có mặt. Header do
caller cung cấp được giới hạn 64 và reject tên nhạy cảm (`authorization`,
`cookie`, `password`, `secret`, `token`) hoặc Spring type header bắt đầu `__`.

## 16. Security

```yaml
spring:
  kafka:
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: SCRAM-SHA-512
      sasl.jaas.config: ENC(...)
    ssl:
      trust-store-location: ${KAFKA_TRUSTSTORE_LOCATION}
      trust-store-password: ENC(...)
```

Không log credential, plaintext sau decrypt, payload hoặc arbitrary headers.
Không bật insecure TLS và không đưa auth token vào message header.
Destination/topic/exchange/routing key phải là constant hoặc mapping tin cậy,
không lấy trực tiếp từ HTTP/user input. Broker ACL phải giới hạn principal theo
đúng topic/exchange; platform cũng reject tên ngoài tập `[A-Za-z0-9._-]`.
Kafka `max.request.size` và broker limit phải giới hạn serialized payload; Rabbit
publisher kiểm tra message sau conversion và reject body lớn hơn 1 MiB.

## 17. Jasypt encryption

Jasypt starter và property crypto thuộc `platform-core`. Queue không chứa
dependency hoặc decryptor Jasypt. Core-owned Environment integration resolve
`ENC(...)` trước khi Boot bind `spring.kafka.*` hoặc `spring.rabbitmq.*`.

## 18. ENC(...) examples

```yaml
spring:
  rabbitmq:
    password: ENC(...)
  kafka:
    properties:
      sasl.jaas.config: ENC(...)
```

Master password và operator workflow được cấu hình duy nhất theo tài liệu
`platform-logging`; không commit hoặc log secret.

## 20. Migration from old platform.queue.kafka.*

```yaml
# OLD
platform.queue.kafka.bootstrap-servers: [localhost:9092]
platform.queue.kafka.producer.acks: all

# NEW
platform.queue.provider: kafka
spring.kafka.bootstrap-servers: [localhost:9092]
spring.kafka.producer.acks: all
```

Logical destinations are removed. Pass the physical topic to
`MessagePublisher`, define topics as application `NewTopic` beans, and migrate
custom consumers to `@KafkaListener`.
Các API named broker, base consumer, batch/bulk staging, topology, retry/DLT và
queue transaction cũ đã bị xóa trong breaking migration này; dùng native Spring
Kafka APIs.

## 21. Migration from old platform.queue.rabbitmq.*

```yaml
# OLD
platform.queue.rabbitmq.host: rabbitmq
platform.queue.rabbitmq.password: ENC(...)

# NEW
platform.queue.provider: rabbitmq
spring.rabbitmq.host: rabbitmq
spring.rabbitmq.password: ENC(...)
```

Define `Queue`, `Exchange` and `Binding` beans in the application. There is no
runtime Kafka-to-Rabbit fallback and no implicit topology declaration by the
platform.
