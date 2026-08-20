# platform-security

`platform-security` provides small, Spring-native contracts for identity normalization, API-key authentication,
authority mapping, tenant isolation, safe error responses and security-event adaptation. Spring Security remains the
authentication and authorization framework; Spring Boot remains responsible for OAuth2/OIDC auto-configuration.

The module targets Java 25, Spring Boot 4 and Spring Security 7 on the Servlet stack.

## Design boundaries

The library deliberately does not create a global `SecurityFilterChain`, guess protected paths, enable method
security, disable CSRF globally, parse or sign JWTs, implement OAuth2/OIDC protocols, store users/clients/API keys,
manage sessions, configure CORS, generate signing keys, or wrap native Spring properties.

There are no `platform.security.*` properties. Applications opt into policy through their own Spring Security filter
chains and native `spring.security.*` properties.

## Decision table

| Requirement | Recommended |
| --- | --- |
| Microservice receives JWT | OAuth2 Resource Server JWT |
| Need immediate token revocation | Opaque Token / Introspection |
| Browser SSO | OAuth2 Login + OIDC |
| Keycloak | Standard OIDC |
| Azure Entra | Standard OIDC |
| Service-to-service modern | Client Credentials |
| Legacy internal API | API Key |
| Legacy tool/integration | Basic Auth |
| Own Identity Provider | Spring Authorization Server |
| Multiple realms/issuers | `AuthenticationManagerResolver` |
| Tenant authorization | `AuthorizationManager` / method security |

## Dependencies and optionality

The artifact has core Spring Security web/config support. OAuth2 Resource Server, JOSE, OAuth2 Client and
Authorization Server dependencies are compile-time optional so a consumer adds only the native starter it uses:

```xml
<dependency>
    <groupId>com.company.platform</groupId>
    <artifactId>platform-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

Use `spring-boot-starter-oauth2-client` for browser login and
`spring-boot-starter-oauth2-authorization-server` only in a dedicated identity service. All versions come from the
platform parent BOM. No vendor SDK, JWT library, Redis client or secret-encryption dependency is included.

## Auto-configuration

Auto-configuration is registered through `AutoConfiguration.imports`:

- `PlatformSecurityAutoConfiguration`: normalized current context, authenticated-tenant resolution, tenant
  authorization and RFC 9457-style 401/403 components.
- `JwtAuthorityAutoConfiguration`: standard/Keycloak-compatible authority mapping and native
  `JwtAuthenticationConverter`.
- `OAuth2IdentityAutoConfiguration`: OIDC/OAuth2 principal normalization when the client dependency exists.
- `ApiKeySecurityAutoConfiguration`: activates only when an `ApiKeyValidator` bean exists; it creates the resolver,
  converter, provider and filter factory but never installs a filter.
- `MultiTenantSecurityAutoConfiguration`: activates only when `TrustedIssuerRepository` and JWT support exist.
- `SecurityEventAutoConfiguration`: activates only when an application supplies `SecurityAuditEventPublisher`.

Every default bean backs off. In particular, applications can replace `CurrentSecurityContext`, `TenantResolver`,
`TenantAuthorization`, `SecurityProblemDetailFactory`, `AuthenticationEntryPoint`, `AccessDeniedHandler`,
`SecurityAuthorityMapper`, `JwtAuthenticationConverter`, `ApiKeyResolver`, `ApiKeyAuthenticationProvider`,
`ApiKeyAuthenticationFilterFactory`, `AuthenticationManagerResolver` and Spring's native decoder/introspector beans.

## JWT resource server and OIDC providers

Keycloak, Okta, Auth0, Azure Entra ID, Google, Ping Identity and ForgeRock are treated as standard OIDC providers.
Issuer discovery, bearer-token extraction, signature verification and claim validation remain native:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER_URI}
          audiences:
            - platform-api
```

Boot 4 also supports `jwk-set-uri`, `public-key-location`, `jws-algorithms`, `principal-claim-name`,
`authorities-claim-name`, `authority-prefix` and `authorities-claim-delimiter`. Prefer Boot's simple authority
properties when they are sufficient. The platform converter adds value when claims span `scope`, `scp`, `roles`,
`groups`, `authorities`, Keycloak `realm_access.roles` or `resource_access`.

```java
@Bean
SecurityFilterChain api(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
    http.securityMatcher("/api/**")
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .anyRequest().authenticated())
        .oauth2ResourceServer(resource -> resource.jwt(jwt ->
            jwt.jwtAuthenticationConverter(converter)));
    return http.build();
}
```

To customize mapping, supply one `SecurityAuthorityMapper` bean. To customize validation, supply Spring's native
`JwtDecoder` and compose `JwtValidators`, `JwtIssuerValidator`, `JwtTimestampValidator` or `JwtClaimValidator`.

## Opaque bearer tokens

No custom introspection client exists. Add the Resource Server starter and configure:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        opaquetoken:
          introspection-uri: ${OAUTH2_INTROSPECTION_URI}
          client-id: ${OAUTH2_INTROSPECTION_CLIENT_ID}
          client-secret: ${OAUTH2_INTROSPECTION_CLIENT_SECRET}
```

Use `http.oauth2ResourceServer(resource -> resource.opaqueToken(withDefaults()))`. An application-supplied
`OpaqueTokenIntrospector` naturally overrides Boot's default.

## Browser SSO

Browser login stays stateful and retains CSRF protection. Do not share its filter chain with stateless APIs.

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            provider: keycloak
            client-id: ${OIDC_CLIENT_ID}
            client-secret: ${OIDC_CLIENT_SECRET}
            authorization-grant-type: authorization_code
            scope: [openid, profile, email]
        provider:
          keycloak:
            issuer-uri: ${OIDC_ISSUER_URI}
```

Use `http.oauth2Login(withDefaults())`. SAML2 remains available through Spring Security and
`spring.security.saml2.relyingparty.*`; this module intentionally does not force the SAML dependency.

## API key authentication

An application owns validation and persistence:

```java
@Bean
ApiKeyValidator apiKeyValidator(ApiKeyRepository repository) {
    return rawKey -> repository.validateHashedKey(rawKey)
        .orElseThrow(() -> new BadCredentialsException("Invalid API key"));
}
```

The returned `ApiKeyPrincipal` contains only an ID, display name, tenant, authorities/scopes, expiry and sanitized
metadata. It must never contain the raw key. The default resolver reads `X-API-Key`; replace `ApiKeyResolver` to use a
different transport.

The application chooses the path and inserts exactly one filter into Spring Security's chain:

```java
@Bean
@Order(1)
SecurityFilterChain internal(HttpSecurity http, ApiKeyAuthenticationFilterFactory filters) throws Exception {
    var matcher = PathPatternRequestMatcher.pathPattern("/internal/**");
    http.securityMatcher(matcher)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.ignoringRequestMatchers(matcher))
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .addFilterBefore(filters.create(matcher), BearerTokenAuthenticationFilter.class);
    return http.build();
}
```

The filter is not a component or servlet registration, so it cannot execute twice. Production storage should retain
only a key ID/prefix, slow secure hash, status, tenant, scopes and lifecycle metadata. Support rotation, revocation,
expiry and multiple client keys in that application-owned store. Never log the header or raw key.

## Basic authentication

Basic authentication is native Spring Security and intended for constrained legacy integrations:

```java
http.securityMatcher("/basic/**").httpBasic(withDefaults());
```

Use `spring.security.user.*` only for local demos. Production applications provide `UserDetailsService` or an
`AuthenticationProvider` and a delegating/BCrypt/Argon2 password encoder. Plaintext, `{noop}`, MD5 and SHA-1 password
storage are not production options.

## Multi-tenancy

The default `TenantResolver` derives tenant identity only after authentication, in this priority:

1. validated API-key principal;
2. verified JWT `tenant_id` claim, then verified issuer;
3. authenticated OIDC `tenant_id`, then OIDC issuer.

An `X-Tenant-Id` header is never treated as identity. It may be a routing hint only after comparing it with the
authenticated tenant.

For dynamic issuers, supply a trust registry:

```java
@Bean
TrustedIssuerRepository trustedIssuerRepository(TenantRegistry registry) {
    return issuer -> registry.containsActiveIssuer(issuer);
}
```

The resulting `AuthenticationManagerResolver<HttpServletRequest>` delegates issuer extraction to Spring's
`JwtIssuerAuthenticationManagerResolver`. It checks a canonical HTTPS URI against the trust repository before OIDC
discovery or JWK access, preventing arbitrary-issuer SSRF. Managers are held in a bounded 100-entry LRU cache and
trust is rechecked on every resolution. Override the resolver if onboarding scale or eviction requirements differ.

Use it in the application chain with
`oauth2ResourceServer(resource -> resource.authenticationManagerResolver(resolver))`. For a fixed issuer, prefer
Boot's single native `issuer-uri` configuration.

Tenant-aware method authorization is opt-in through Spring's standard annotation:

```java
@EnableMethodSecurity
class SecurityConfiguration {}

@PreAuthorize("@tenantAuthorization.canAccess(authentication, #tenantId)")
public Customer read(String tenantId, String customerId) { ... }
```

Applications with shared identities or cross-tenant administrators should replace `TenantAuthorization` with their
explicit policy. Never authorize only from a path variable or tenant header.

## Current identity

Inject `CurrentSecurityContext`; it reads Spring's `SecurityContextHolder` strategy and returns a sanitized immutable
`SecurityPrincipal`. It does not create another context or `ThreadLocal`. Only `iss`, `tenant_id` and `organization`
attributes are exposed by default; tokens, credentials and arbitrary private claims are excluded.

For asynchronous work, use Spring Security context propagation facilities. Do not copy the identity into a second
unmanaged thread-local.

## Self-hosted Authorization Server

Run this role as a dedicated identity service in production. Add Boot's Authorization Server starter and use native
configuration:

```yaml
spring:
  security:
    oauth2:
      authorizationserver:
        issuer: ${AUTH_ISSUER}
        multiple-issuers-allowed: false
        client:
          platform-client:
            registration:
              client-id: platform-client
              client-secret: ${AUTH_CLIENT_SECRET}
              client-authentication-methods: [client_secret_basic]
              authorization-grant-types: [authorization_code, refresh_token, client_credentials]
              redirect-uris: [https://app.example.com/login/oauth2/code/platform-client]
              scopes: [openid, profile, platform.read, platform.write]
```

Spring Authorization Server owns authorization, token, JWK, introspection, revocation, UserInfo, OIDC and refresh
token protocols. Production must supply persistent `RegisteredClientRepository`, `OAuth2AuthorizationService` and
`OAuth2AuthorizationConsentService`, plus stable `JWKSource<SecurityContext>`/`JwtEncoder` backed by Vault, KMS, HSM,
PKCS#11 or managed secrets. Do not generate a new random RSA key at restart. Use `OAuth2TokenCustomizer<JwtEncodingContext>`
for tenant, permission or organization claims.

The module does not ship in-memory production repositories, a token blacklist, custom refresh-token entities,
custom endpoints or signing keys. Prefer opaque tokens or short-lived JWTs when immediate revocation is required.

## Errors, events and observability

The reusable entry point and denied handler return generic `application/problem+json` responses with 401 and 403.
They exclude exception messages, token details, claims and stack traces. Supplying any application
`AuthenticationEntryPoint` or `AccessDeniedHandler` makes the defaults back off.

To bridge Spring authentication/authorization events into an audit sink, provide `SecurityAuditEventPublisher`.
Normalized events contain outcome, authentication type and failure category, never credentials, tokens, cookies or
principal PII. Do not use user IDs, emails, keys, token values or unbounded tenant IDs as metric tags. Micrometer,
OpenTelemetry and Actuator remain application concerns; the module introduces no parallel telemetry framework.

## Production hardening

- Use TLS, validate issuer and audience, keep JWT lifetime short, and rotate signing keys without reusing key IDs.
- Keep CSRF for browser/session/cookie chains; ignore it only for specifically matched stateless credential chains.
- Define CORS through `CorsConfigurationSource`; never combine wildcard origins with credentials.
- Configure HSTS, CSP, frame, referrer and permissions policies through Spring Security headers.
- Keep secrets in environment-backed configuration, Vault, Kubernetes Secrets or a cloud secret manager.
- Never enable Spring Security `TRACE` by default or log Authorization, cookies, credentials or private claims.
- Prefer client credentials over Basic/API keys where an OAuth2 infrastructure exists.

## Showcase

The runnable example is in `examples/platform-security-showcase`. It uses separate filter chains and profiles:
`resource-server`, `opaque-token`, `api-key`, `basic`, `oidc-login`, `multi-tenant`, and `authorization-server`.
Its in-memory API key and sample users are development-only.

```bash
./mvnw -pl examples/platform-security-showcase -am -DskipTests package
java -jar examples/platform-security-showcase/target/platform-security-showcase-1.0.0-SNAPSHOT.jar
SPRING_PROFILES_ACTIVE=api-key DEMO_API_KEY=local-demo-key \
  java -jar examples/platform-security-showcase/target/platform-security-showcase-1.0.0-SNAPSHOT.jar
```

Endpoints are `/public/hello`, `/api/me`, `/api/admin`, `/api/tenant/{tenantId}`, `/internal/health`, `/basic/me`
and `/web/me`. Profiles that require an external issuer intentionally do not embed credentials or start fake remote
connections during the default run.

## Migration

Replace custom JWT filters/utilities with the Resource Server starter and native properties. Move endpoint policy
into application-owned filter chains, replace static security helpers with injected `CurrentSecurityContext`, expose
an `ApiKeyValidator` instead of configuring plaintext keys, and explicitly register trusted dynamic issuers. Existing
application beans take precedence, making adoption incremental.
