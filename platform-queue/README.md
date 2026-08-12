# platform-queue

`platform-queue` cung cấp contract publish/consume cross-provider, logical
destination/subscription, topology policy, DLT, inbox/outbox và observability.
Module không sở hữu cấu hình kết nối Kafka/RabbitMQ.

## Cấu hình native trước

```yaml
spring:
  application:
    name: order-service
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.ByteArraySerializer
      acks: all
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.ByteArrayDeserializer
      enable-auto-commit: false

platform:
  queue:
    brokers:
      main:
        provider: KAFKA
    destinations:
      order-created:
        broker: main
        kafka:
          topic: order.created.v1
    subscriptions:
      order-projector:
        destination: order-created
        kafka:
          group-id: order-projector
```

Kafka bootstrap/client/producer/consumer/admin/security/SSL/transaction settings
thuộc `spring.kafka.*`. Rabbit host/port/address/credential/SSL/connection cache,
publisher confirm và listener defaults thuộc `spring.rabbitmq.*`.

Để contract publish Rabbit chỉ trả `CONFIRMED` sau confirm/return đáng tin cậy,
ứng dụng phải bật:

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated
    publisher-returns: true
    template:
      mandatory: true
```

Auto-configuration chỉ sử dụng các bean Spring Boot đã quản lý:
`KafkaTemplate`, `ConcurrentKafkaListenerContainerFactory`, `KafkaAdmin`,
`RabbitTemplate`, `SimpleRabbitListenerContainerFactory` và `AmqpAdmin`. Logical
broker aliases cùng provider dùng chung native connection; nếu ứng dụng cần
multi-cluster/multi-vhost, ứng dụng phải cung cấp bean native đã cấu hình rõ ràng
thay vì nhét connection settings vào `platform.queue.*`.

Các property `platform.queue.*` còn lại chỉ mô tả behavior riêng của platform:
logical routing, message envelope limits, topology declaration, delivery
inbox/outbox, DLT/retry policy cross-provider và observability.
