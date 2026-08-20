# Platform Security Showcase

This application demonstrates `platform-security`; it is not a production identity service.

Build and run the safe default profile:

```bash
./mvnw -pl examples/platform-security-showcase -am -DskipTests package
java -jar examples/platform-security-showcase/target/platform-security-showcase-1.0.0-SNAPSHOT.jar
curl http://localhost:8080/public/hello
```

Activate one or more non-overlapping profiles with `SPRING_PROFILES_ACTIVE`. Do not activate `resource-server`,
`opaque-token`, and `multi-tenant` together because they intentionally own the same `/api/**` matcher.

- `api-key`: set `DEMO_API_KEY`, then call `/internal/health` with `X-API-Key`. The validator is development-only.
- `basic`: set `BASIC_PASSWORD`, then call `/basic/me` using native HTTP Basic.
- `resource-server`: optionally set `OIDC_ISSUER_URI`; protects `/api/**` with JWT.
- `opaque-token`: set the three `OAUTH2_INTROSPECTION_*` variables.
- `oidc-login`: set `OIDC_CLIENT_SECRET` and optionally issuer/client ID.
- `multi-tenant`: demonstrates two HTTPS issuer trust entries without contacting them until a bearer request arrives.
- `authorization-server`: set `AUTH_USER_PASSWORD` and a securely encoded `AUTH_CLIENT_SECRET`; uses Boot's native
  Authorization Server auto-configuration. Its default repositories are demonstration-only.

See the module-level `platform-security/README.md` for architecture and production hardening.
