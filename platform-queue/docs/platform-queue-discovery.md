# Platform Queue Discovery

- Spring Boot 4.0.7 manages Spring Kafka 4.0.6 and Spring AMQP 4.0.4.
- `spring-boot-kafka` and `spring-boot-amqp` are optional provider dependencies.
- Boot owns producer/consumer factories, templates, admins, connection
  factories and listener factories.
- `platform.queue` binds only `enabled` and `provider` (`KAFKA`, `RABBITMQ`).
- Provider adapters consume Boot-managed `KafkaTemplate` or `RabbitTemplate`.
- Native `spring.kafka.*`, `spring.rabbitmq.*`, `management.*` and
  `jasypt.encryptor.*` namespaces are not mirrored.
- Public reliability SPIs are retained without default in-memory or database
  implementations.
