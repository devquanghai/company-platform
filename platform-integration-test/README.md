# Platform integration test

Runnable Spring Boot application proving that `platform-core`,
`platform-cache`, `platform-queue`, `platform-service-exchange`,
`platform-logging`, Lombok `@Slf4j` and Spring Boot Micrometer Observation work
in one application context.

## Cache and queue end-to-end test

`PlatformCacheQueueE2ETest` activates the `integration-e2e` profile and starts
real Redis, Kafka and RabbitMQ instances with Testcontainers. Docker must be
running.

```bash
./mvnw -pl platform-integration-test -am \
  -Dtest=PlatformCacheQueueE2ETest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

The scenario enables and verifies:

- Caffeine, Redis, multi-level, fallback and NOOP cache definitions;
- typed and annotation-driven Spring Cache operations;
- Redis atomic, compare-and-set, optimistic update and distributed locking;
- Kafka and RabbitMQ topology, producers and annotation-driven consumers;
- direct publishing, inbox idempotency and transactional outbox polling;
- cache/queue health, metrics, tracing and audit event beans.

The distributed lock, inbox and outbox stores used here are test adapters. A
consuming service must provide durable production implementations.

## HTTP integration application

Start an HTTP service on port `18080` exposing `/echo`, or override
`INTEGRATION_ECHO_BASE_URL`, then run:

```bash
../mvnw -pl platform-integration-test -am spring-boot:run
```

Call:

```bash
curl 'http://localhost:8080/platform/integration?email=alice@example.com'
```

The response contains upstream status/body, the masked email and the timestamp
supplied by `platform-core`. Trace/span IDs are populated when a Micrometer
Tracing handler is configured; the integration code does not depend directly on
an OpenTelemetry API.
