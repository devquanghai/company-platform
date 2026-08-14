# Platform Queue Design

The module owns only provider selection and provider-neutral publishing.

```text
api.publish <- publish internal adapter <- Boot-managed template
                                         <- spring.kafka.*
                                         <- spring.rabbitmq.*
```

Consumer registration, topology, retry, dead-letter, transactions, client
security, serialization, durable inbox/outbox and broker observability use
native Spring Kafka, Spring AMQP, Spring Boot and application infrastructure.

Delivery is at-least-once where consumers use broker redelivery. A successful
Kafka send completion is `PUBLISHED`, not a durability claim; timeout is an
explicit unknown outcome. Rabbit publish waits for correlated confirm and
mandatory return and reports `CONFIRMED`, `REJECTED` or `RETURNED`. No
cross-broker fallback exists.
