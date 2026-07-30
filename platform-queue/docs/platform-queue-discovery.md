# Platform Queue Discovery

## Repository baseline

- Root `pom.xml` is both the shared parent and reactor aggregator:
  `com.company.platform:platform-parent:1.0.0-SNAPSHOT`.
- There are no separate `platform-parent/` or `platform-dependencies/` modules.
  Dependency management is defined directly in the root POM.
- `platform-queue` is already listed in the reactor and dependency management.
  Its current POM is an empty JAR placeholder with no source.
- Package convention is `com.company.platform.<module>`.
- Maven must run on JDK 25 (`maven-enforcer-plugin`), while produced bytecode
  targets Java 21 (`maven.compiler.release=21`).
- Auto-configuration uses `@AutoConfiguration`, explicit
  `AutoConfiguration.imports`, `@ConditionalOnMissingBean`, and no component scan.
- Tests use JUnit 5, AssertJ, Mockito, `ApplicationContextRunner`, Surefire and
  Failsafe. Queue coverage must override the root gate to 85% line / 80% branch.

## Managed versions

| Capability | Managed version/source |
|---|---|
| Spring Boot | 4.0.7, root `spring-boot.version` |
| Spring Framework | 7.0.8, Spring Boot BOM |
| Spring Kafka | 4.0.6, Spring Boot BOM |
| Apache Kafka client | 4.1.2, Spring Boot BOM |
| Spring AMQP | 4.0.4, Spring Boot BOM |
| RabbitMQ Java client | 5.27.1, Spring Boot BOM |
| Jackson | root declares 3.2.1, but the effective built classpath is 3.1.4 from Spring Boot BOM |
| Micrometer | 1.16.6, Spring Boot BOM |
| Micrometer Tracing | 1.6.6, Spring Boot BOM |
| Resilience4j | 2.4.0, root Resilience4j BOM |
| Testcontainers | 2.0.5, Spring Boot BOM |

No dependency version will be declared in `platform-queue`.

## Reusable platform abstractions

- Time: `com.company.platform.core.time.TimeProvider` and
  `SystemTimeProvider`.
- JSON/Jackson 3: `com.company.platform.core.json.JsonMapperHelper`; it uses the
  application-managed strict `JsonMapper` and normalizes mapping failures.
- Request/correlation: `RequestContextProvider` and
  `MdcRequestContextProvider`.
- Trace context: `TraceContextProvider`, `CurrentTraceContext`, and
  `TraceHeaders`.
- Current actor: `CurrentUserProvider`.
- Stable queue error keys already exist in
  `com.company.platform.core.exception.code.QueueCode`.
- Platform failures: `PlatformIntegrationException`,
  `PlatformInfrastructureException`, and other `PlatformException` variants.
- Spring application events are the established default event transport
  (`ApplicationEventPublisher`), used by core audit and service-exchange.
- Payload/error sanitization has a built-in fail-safe baseline. When present,
  `com.company.platform.logging.api.masking.DataMaskingService` and
  `SanitizedThrowable` strengthen it without making `platform-logging`
  mandatory.
- Micrometer abstractions and low-cardinality meters follow the
  `platform-service-exchange` and `platform-logging` conventions.

There is no existing queue envelope, Kafka/Rabbit adapter, distributed inbox,
outbox store, or generic broker health abstraction to reuse.

## Architecture constraints and risks

- Keep provider-neutral ports/models free of Kafka, AMQP, channel, producer and
  consumer native types. Broker-specific controls belong to Kafka/Rabbit APIs.
- Delivery is at-least-once. Kafka transactions cover Kafka read/process/write
  and offset commit only; database consistency requires outbox/inbox.
- Never route automatically between Kafka and RabbitMQ and never use an
  in-memory production durability fallback.
- `spring-kafka` and `spring-rabbit` are optional dependencies guarded by
  `@ConditionalOnClass`; runtime resources are built only for enabled named
  brokers.
- Root declares Jackson 3.2.1 after the Spring Boot BOM, but existing Surefire
  classpaths resolve `tools.jackson` 3.1.4; Springdoc also brings the separate
  `com.fasterxml.jackson` 2.21.4 namespace. This pre-existing BOM-ordering risk
  must be checked by a dependency-convergence release gate. Queue serializers
  must use the repository's `JsonMapperHelper` rather than create a private
  mapper or mix namespaces.
- The strict platform mapper fails on unknown properties by default. Envelope
  evolution and upcasting must occur before binding to a trusted payload type.
- Spring application events are synchronous by default; the queue audit adapter
  must implement fail-open behavior and a recursion guard itself.
- Broker credentials, SSL material, payloads, exception stacks and arbitrary
  type names must not enter logs, headers, actuator details or validation
  errors.
- Existing worktree changes in `platform-cache`, `platform-core`,
  `platform-logging`, integration-test configuration and `.codex/config.toml`
  are user-owned and must be preserved.

## Implementation direction

Use one artifact with hexagonal package boundaries, named broker/destination
registries, explicit provider adapters, immutable envelope/metadata, bounded
retry classification, confirmation-aware publish results, listener-container
registration, inbox/outbox SPIs and poller, metrics/tracing/health/audit ports,
configuration validation, explicit topology governance, tests and documentation.
