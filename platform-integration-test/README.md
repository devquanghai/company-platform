# Platform integration test

Runnable Spring Boot application proving that `platform-core`,
`platform-service-exchange`, `platform-logging`, Lombok `@Slf4j` and Spring Boot
OpenTelemetry work in one application context.

Start an HTTP service on port `18080` exposing `/echo`, or override
`INTEGRATION_ECHO_BASE_URL`, then run:

```bash
../mvnw -pl platform-integration-test -am spring-boot:run
```

Call:

```bash
curl 'http://localhost:8080/platform/integration?email=alice@example.com'
```

The response contains upstream status/body, a real OpenTelemetry trace/span ID,
the masked email and the timestamp supplied by `platform-core`.
