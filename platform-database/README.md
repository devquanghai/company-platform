# Platform Database

## 1. Purpose

`platform-database` is the platform's single reusable JDBC/JPA artifact. It builds the conventional single `HikariDataSource` from Spring Boot's `DataSourceProperties`, `JdbcConnectionDetails`, and Hikari's native `spring.datasource.hikari.*` settings. It also applies safe JPA/Hibernate defaults without creating a platform property namespace.

It deliberately adds no database client API, custom configuration-properties model, entity, repository, routing context, transaction annotation, or connection-pool implementation.

The consuming service owns its entities, repositories, schemas, migrations, transaction boundaries, database driver, and any multi-database topology.

## 2. Architecture

The implementation follows this decision order:

1. Platform datasource auto-configuration adapts Boot-native connection details and builds Hikari from native properties.
2. Spring Data JPA owns repositories, pagination, specifications, entity graphs, auditing, and exception translation.
3. Platform JPA auto-configuration contributes safe defaults through Boot/Hibernate extension points; Hibernate owns ORM, locking, batching, fetching, and native ORM settings.
4. HikariCP owns pooling and pool lifecycle.
5. Spring Boot still owns `JdbcTemplate`, JPA entity-manager factory, transaction managers, health, metrics, and migration integration.
6. Application code is explicit where multiple persistence units cannot be inferred safely.

Auto-configuration is registered through `AutoConfiguration.imports`; there is no component scan:

- `PlatformDataSourceAutoConfiguration` runs before Boot's datasource configuration, builds Hikari when `spring.datasource.type` is absent or selects Hikari, and backs off when the application supplies a `DataSource`.
- `PlatformJpaAutoConfiguration` contributes `hibernate.jdbc.time_zone=UTC` and `hibernate.query.fail_on_pagination_over_collection_fetch=true` through `HibernatePropertiesCustomizer`, preserving consumer overrides and backing off for an application customizer.
- `PlatformDatabaseDefaultsEnvironmentPostProcessor` adds low-precedence native defaults: `spring.jpa.open-in-view=false`, `spring.jpa.show-sql=false`, and `spring.jpa.hibernate.ddl-auto=validate`. Application configuration has higher precedence.

No additional public abstraction is required. The module has no `contract` package; its public classes under `internal` exist only because Spring Boot must instantiate registered auto-configurations.

## 3. Dependencies

Add the platform artifact and the one JDBC driver required by the service:

```xml
<dependency>
    <groupId>com.company.platform</groupId>
    <artifactId>platform-database</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

`spring-boot-starter-data-jpa` transitively supplies Spring JDBC, Spring Transaction, Hibernate ORM, Jakarta Persistence, and HikariCP. Versions are owned by the parent Spring Boot BOM. This module does not bundle a JDBC driver, Actuator, migration engine, Jasypt, or dynamic-datasource.

## 4. Single datasource

### PostgreSQL

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/app}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

### Oracle

The service must add the BOM-managed `com.oracle.database.jdbc:ojdbc11` or `ojdbc17` driver appropriate for its runtime.

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:oracle:thin:@//localhost:1521/FREEPDB1}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

Do not set `driver-class-name` when Boot can infer the driver from the JDBC URL. Do not put credentials in a JDBC URL.

`JdbcConnectionDetails` remains the connection contract. This preserves Boot service connections such as Testcontainers and lets an application-provided connection-details bean override values from `spring.datasource.*` without introducing another property model.

## 5. Hikari configuration

This is a starting point, not a universal sizing recommendation:

```yaml
spring:
  datasource:
    hikari:
      pool-name: ${spring.application.name}-db-pool
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      validation-timeout: 5000
      idle-timeout: 600000
      max-lifetime: 1800000
      keepalive-time: 120000
      leak-detection-threshold: 0
      auto-commit: true
      read-only: false
```

All keys above are Hikari JavaBean properties bound natively beneath `spring.datasource.hikari`. `leak-detection-threshold: 0` disables leak diagnostics; enable it only for targeted investigation with a threshold longer than legitimate transactions. Keepalive must be shorter than max lifetime, and max lifetime should be shorter than database or network connection limits.

Pool size depends on database capacity, total application replicas, request concurrency, transaction duration, and the database worker model. Load-test the complete deployment; multiplying a locally reasonable pool by many replicas can exhaust the database.

## 6. JPA production configuration

```yaml
spring:
  jpa:
    open-in-view: false
    show-sql: false
    hibernate:
      ddl-auto: validate
```

Use migrations for schema changes. Never use `create`, `create-drop`, or `update` in production. With open-session-in-view disabled, fetch required data inside a clear service transaction and map entities to API DTOs there.

These three values are module defaults and can be overridden with the same native Spring Boot properties. There is no `platform.database.*` alias.

## 7. Hibernate configuration

Hibernate-native settings remain under `spring.jpa.properties.hibernate`:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
          batch_size: 50
        order_inserts: true
        order_updates: true
        default_batch_fetch_size: 50
        format_sql: false
        generate_statistics: false
        query:
          fail_on_pagination_over_collection_fetch: true
```

The module already defaults `hibernate.jdbc.time_zone` to `UTC` and `hibernate.query.fail_on_pagination_over_collection_fetch` to `true` using Hibernate 7 constants. Explicit application values win. The remaining settings in the example are workload-dependent and are not forced globally.

Treat batch and fetch sizes as measured starting points. Identity ID generation can limit insert batching. For large jobs, use bounded transactions and explicit `flush`/`clear` to control memory. Prefer UTC storage; this module performs no silent timezone conversion.

For N+1 queries, use `@EntityGraph`, fetch joins, projections, or measured batch fetching. Use JPA `@Version` for optimistic locking and `@Lock`/`LockModeType` for pessimistic locking.

## 8. Transaction management

Use Spring's `@Transactional`. Keep transactions short and do not hold one open across HTTP, Kafka, RabbitMQ, or other remote calls. Database write retries must be explicit, limited to classified transient failures, and safe for the operation's idempotency.

For multiple persistence units, always name the transaction manager:

```java
@Transactional(transactionManager = "customerTransactionManager")
public void updateCustomer(...) {
    // customer persistence unit only
}
```

Cross-database or database/message consistency is an application architecture decision such as transactional outbox, idempotent consumer, or saga. This module does not implement pseudo-XA or enable JTA.

## 9. Multiple databases

Multiple entity/repository models require explicit persistence units in the consuming service. Each unit needs its own `DataSource`, `LocalContainerEntityManagerFactoryBean`, `JpaTransactionManager`, entity packages, repository packages, qualifiers, and one deliberate `@Primary` selection where framework conventions require it.

Spring Boot does not define arbitrary `spring.datasource.customer.*` or `spring.datasource.payment.*` trees. This module does not parse such trees, discover entities, scan the classpath, guess the primary database, or create transaction managers. If the service binds application-owned settings for explicit bean configuration, that namespace and configuration class belong to the service.

Runtime routing is a separate use case. No dynamic-datasource dependency is included because this repository has not selected or compatibility-tested one. If adopted later, use the verified library's native `spring.datasource.dynamic.*` properties and annotation, enable strict unknown-datasource behavior, resolve routing before transaction creation, and integration-test nested calls and transaction binding. Do not use routing for unrelated schemas/entity models or switch a datasource inside an active JPA transaction.

Read/write replication should preferentially be handled by database or infrastructure routing. Tenant-per-database requires an explicit multi-tenancy design, not a generic ThreadLocal router.

## 10. Migration

The repository has not selected Liquibase or Flyway, so neither is forced on every consumer. Select exactly one in the application and use its Boot integration.

Liquibase:

```xml
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>
```

```yaml
spring:
  liquibase:
    change-log: classpath:/db/changelog/db.changelog-master.yaml
```

Flyway for PostgreSQL:

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

```yaml
spring:
  flyway:
    locations: classpath:db/migration
    validate-on-migrate: true
```

Keep `ddl-auto: validate` so invalid mappings or missing migration changes fail startup. Multi-database migrations must have explicit datasource/lifecycle wiring and integration tests proving each changelog targets the intended database.

## 11. Jasypt ownership

Prefer Kubernetes Secrets, Vault, a secret manager, environment variables, or config trees. When encrypted configuration is required, the Jasypt starter and `ENC(...)` Environment integration are supplied centrally by `platform-core`; database must not add its own decryptor or Jasypt dependency:

```yaml
spring:
  datasource:
    username: ENC(...)
    password: ENC(...)
```

Externalize the master password. Never commit it, log decrypted values, expose them through Actuator, or add a custom decryptor in this module.

## 12. Security

- Supply credentials from a secret store or environment and grant the database user least privilege.
- Never log datasource passwords, Jasypt master keys, credentials embedded in URLs, SQL bind values, tokens, or sensitive query payloads.
- Keep Hibernate SQL and parameter logging disabled in production.
- Restrict Actuator endpoints and prevent values from being shown by environment/config endpoints.
- Use database TLS with certificate verification according to the selected JDBC driver's native properties; never add a trust-all mode.

## 13. Observability and health

When the consuming application adds `spring-boot-starter-actuator` and a Micrometer registry, Boot exposes standard datasource health and Hikari pool metrics. This module adds no duplicate health query, scheduled `SELECT 1`, pool binder, repository AOP timer, or SQL proxy. Use database slow-query logs, APM, or OpenTelemetry for query latency analysis.

## 14. Entity and query guidance

- Choose an ID strategy compatible with the database and batching needs.
- Avoid primitive fields for nullable columns and be careful with proxy-safe equality.
- Prefer lazy relationships; avoid unnecessary bidirectional mappings and unconditional `CascadeType.ALL`.
- Do not serialize entities directly as REST models.
- Use Spring Data `Pageable`, `Page`, `Slice`, `Sort`, specifications, and projections directly.
- Keep transaction scope short and query plans/indexes observable at the database.
- Enable Spring Data JPA auditing in the application only when needed; no base entity is required.

## 15. Testing

Four focused auto-configuration tests verify native datasource/Hikari binding, application `DataSource` back-off, low-precedence JPA defaults, Hibernate constants, and override preservation.

The module integration test uses a real PostgreSQL Testcontainer, a schema initialized before startup, the module's default `ddl-auto=validate`, a real Spring Data repository, and the Boot-configured transaction manager. It verifies:

- application context, datasource connection, entity mapping, and schema validation;
- Hikari as the datasource plus native Hikari property binding;
- safe JPA and Hibernate defaults applied to the real entity-manager factory;
- repository CRUD and committed transaction visibility;
- rollback after a runtime failure.

The test is skipped when Docker is unavailable. Services should add database-specific Testcontainers tests for migrations, native queries, locking, and every explicit persistence unit. H2 is not evidence of PostgreSQL or Oracle compatibility.

Run:

```bash
./mvnw -pl platform-database -am clean verify
```

## 16. Production checklist

- [ ] Database credentials come from Secret/Vault/environment/config tree.
- [ ] No production password or Jasypt master key is in Git.
- [ ] `spring.jpa.open-in-view=false`.
- [ ] `ddl-auto=create`, `create-drop`, and `update` are absent in production.
- [ ] Exactly one migration framework is enabled and migration failure stops startup.
- [ ] Pool size is load-tested across the total replica count.
- [ ] Hikari max lifetime is below database/network connection limits.
- [ ] Connection and validation timeouts match the service SLO.
- [ ] SQL and bind-parameter logging are disabled.
- [ ] Database health endpoints are protected and metrics are exported safely.
- [ ] Transaction boundaries and idempotency assumptions are explicit.
- [ ] Every multi-database repository-to-transaction-manager mapping is tested.
- [ ] Dynamic routing, if separately adopted, is strict and transaction-tested.
- [ ] Testcontainers commit, rollback, mapping, and migration tests pass.
- [ ] Migration rollback/roll-forward and recovery procedures are documented.

## 17. Common mistakes

- Creating `platform.database.*` aliases for existing Boot properties.
- Building wrappers around `DataSource`, `EntityManager`, repositories, `JdbcTemplate`, or transaction managers.
- Treating multiple persistence units and runtime datasource routing as the same design.
- Falling back silently to a default datasource after a routing typo.
- Using Hibernate schema update as a migration mechanism.
- Assuming a connection can switch datasource inside an active JPA transaction.
- Retrying every database exception or write transaction.
- Hardcoding a pool size without accounting for replicas and database capacity.
- Adding every JDBC driver, both migration engines, or encryption/routing libraries to a shared artifact.
