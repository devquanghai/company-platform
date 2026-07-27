package com.company.platform.core.audit;

import com.company.platform.core.audit.jpa.AuditTrail;
import com.company.platform.core.audit.jpa.SecurityContextAuditorAware;
import com.company.platform.core.audit.jpa.SystemAuditorAware;
import com.company.platform.core.config.audit.PlatformAuditingConfiguration;
import com.company.platform.core.configuration.properties.PlatformAuditProperties;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SuppressWarnings("deprecation")
class AuditInfrastructureTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.of(2026, 7, 23, 1, 2, 3, 0, ZoneOffset.UTC);

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditorAwareUsesPrincipalAndSafeFallbacks() {
        SecurityContextAuditorAware aware = new SecurityContextAuditorAware("system");
        assertThat(aware.getCurrentAuditor()).contains("system");

        Authentication nullName = (Authentication) Proxy.newProxyInstance(
            Authentication.class.getClassLoader(),
            new Class<?>[]{Authentication.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "isAuthenticated" -> true;
                case "getName" -> null;
                default -> null;
            }
        );
        SecurityContextHolder.getContext().setAuthentication(nullName);
        assertThat(aware.getCurrentAuditor()).contains("system");

        TestingAuthenticationToken unauthenticated =
            new TestingAuthenticationToken("ignored", "secret");
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        assertThat(aware.getCurrentAuditor()).contains("system");

        TestingAuthenticationToken authenticated =
            new TestingAuthenticationToken("ada", "secret", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(authenticated);
        assertThat(aware.getCurrentAuditor()).contains("ada");

        TestingAuthenticationToken blank =
            new TestingAuthenticationToken(" ", "secret", "ROLE_USER");
        SecurityContextHolder.getContext().setAuthentication(blank);
        assertThat(aware.getCurrentAuditor()).contains("system");
        assertThat(new SystemAuditorAware("batch").getCurrentAuditor()).contains("batch");
        assertThatNullPointerException().isThrownBy(() -> new SecurityContextAuditorAware(null));
        assertThatNullPointerException().isThrownBy(() -> new SystemAuditorAware(null));
    }

    @Test
    void changeResolverOnlyCollectsExplicitValidChanges() throws Exception {
        DefaultAuditChangeResolver resolver = new DefaultAuditChangeResolver();
        Method method = AuditedService.class.getDeclaredMethod("update", Change.class);
        Change argument = new Change(Map.of("before.status", "NEW"));
        Change result = new Change(Map.of("after.status", "PAID"));
        assertThat(resolver.resolve(method, new Object[]{argument, "ignored"}, result))
            .containsEntry("before.status", "NEW")
            .containsEntry("after.status", "PAID");
        assertThat(resolver.resolve(method, null, new Change(null))).isEmpty();
        assertThat(resolver.resolve(method, new Object[]{
            new ChangeWithInvalidEntries()
        }, null)).isEmpty();
    }

    @Test
    void aspectPublishesSuccessAndFailureWithoutChangingBusinessOutcome() throws Throwable {
        List<Object> events = new ArrayList<>();
        AuditAspect aspect = aspect(events::add, true, request("/orders/1", "PATCH"));
        Method method = AuditedService.class.getDeclaredMethod("update", Change.class);
        ProceedingJoinPoint success = joinPoint(method,
            new Object[]{new Change(Map.of("status", "PAID"))}, "updated", null);
        Audited audited = method.getAnnotation(Audited.class);

        assertThat(aspect.audit(success, audited)).isEqualTo("updated");
        AuditEvent successEvent = (AuditEvent) events.getFirst();
        assertThat(successEvent.getActor()).isEqualTo("ada");
        assertThat(successEvent.getAction()).isEqualTo("UPDATE");
        assertThat(successEvent.getResource()).isEqualTo("order");
        assertThat(successEvent.getLocation()).isEqualTo("/orders/1");
        assertThat(successEvent.getMechanism()).isEqualTo("PATCH");
        assertThat(successEvent.getRequestId()).isEqualTo("request-1");
        assertThat(successEvent.getCorrelationId()).isEqualTo("correlation-1");
        assertThat(successEvent.getTimestamp()).isEqualTo(NOW);
        assertThat(successEvent.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(successEvent.getFailureType()).isNull();
        assertThat(successEvent.getChanges()).containsEntry("status", "PAID");
        assertThat(successEvent.getBusinessContext()).isEqualTo("order");
        assertThat(successEvent.getServiceName()).isEqualTo("application");
        assertThat(successEvent.getEnvironment()).isEqualTo("default");
        assertThat(successEvent.getTraceId()).isEqualTo("trace-1");
        assertThat(successEvent.getSpanId()).isEqualTo("span-1");
        assertThat(successEvent.getApi().getControllerClass())
            .isEqualTo(AuditedService.class.getName());
        assertThat(successEvent.getApi().getMethodName()).isEqualTo("update");
        assertThat(successEvent.getMonitor().getSuccess()).isTrue();
        assertThat(successEvent.getMonitor().getExecutionTimeMs()).isNotNegative();
        assertThat(successEvent.toString()).doesNotContain("PAID");

        IllegalStateException businessFailure = new IllegalStateException("failed");
        ProceedingJoinPoint failure = joinPoint(method, new Object[0], null, businessFailure);
        assertThatThrownBy(() -> aspect.audit(failure, audited)).isSameAs(businessFailure);
        AuditEvent failureEvent = (AuditEvent) events.get(1);
        assertThat(failureEvent.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(failureEvent.getFailureType()).isEqualTo(IllegalStateException.class.getName());
        assertThat(failureEvent.getMonitor().getSuccess()).isFalse();
    }

    @Test
    void aspectSupportsNonHttpCallsDisabledFailureEventsAndAuditSinkFailure() throws Throwable {
        Method method = AuditedService.class.getDeclaredMethod("update", Change.class);
        Audited audited = method.getAnnotation(Audited.class);
        List<Object> events = new ArrayList<>();
        AuditAspect aspect = aspect(events::add, false, request(null, " "));
        IllegalArgumentException failure = new IllegalArgumentException("business");
        assertThatThrownBy(() -> aspect.audit(
            joinPoint(method, null, null, failure), audited)).isSameAs(failure);
        assertThat(events).isEmpty();

        AuditAspect failedSink = aspect(event -> {
            throw new IllegalStateException("sink unavailable");
        }, true, request(null, " "));
        assertThat(failedSink.audit(
            joinPoint(method, new Object[0], "ok", null), audited)).isEqualTo("ok");
        assertThat(aspect(events::add, true, request(null, null)).audit(
            joinPoint(method, new Object[0], "ok", null), audited)).isEqualTo("ok");

        Method noDiffMethod = AuditedService.class.getDeclaredMethod("read");
        Audited noDiff = noDiffMethod.getAnnotation(Audited.class);
        assertThat(aspect(events::add, true, request(null, null)).audit(
            joinPoint(noDiffMethod, new Object[0], "read", null), noDiff)).isEqualTo("read");
        AuditEvent noDiffEvent = (AuditEvent) events.getLast();
        assertThat(noDiffEvent.getBusinessContext()).isNull();
        assertThat(noDiffEvent.getDataChange()).isNull();

        PlatformAuditProperties compatibilityProperties = new PlatformAuditProperties();
        AuditAspect compatibilityAspect = new AuditAspect(
            () -> Optional.of("system"),
            new DefaultAuditChangeResolver(),
            events::add,
            request(null, null),
            fixedTimeProvider(),
            compatibilityProperties
        );
        assertThat(compatibilityAspect).isNotNull();
    }

    @Test
    void eventAndTrailEnforceTheirContracts() {
        AuditEvent event = new AuditEvent(
            " actor ", " UPDATE ", " ", " service#method ", " JAVA ", " ", null,
            NOW, AuditOutcome.SUCCESS, " ", null);
        assertThat(event.getActor()).isEqualTo(" actor ");
        assertThat(event.getResource()).isNull();
        assertThat(event.getChanges()).isEmpty();
        Map<String, Object> nullableChange = new java.util.HashMap<>();
        nullableChange.put("nullable", null);
        AuditEvent failed = new AuditEvent(
            "actor", "UPDATE", "order", "/orders/1", "PATCH", "request-1", "correlation-1",
            NOW, AuditOutcome.FAILURE, "failure", nullableChange
        );
        assertThat(failed.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(failed.getFailureType()).isEqualTo("failure");
        assertThat(failed.getDataChange().getFieldChanges().getFirst().getType()).isNull();
        assertThatIllegalArgumentException().isThrownBy(() -> event(null, "A", "L", "M"));
        assertThatIllegalArgumentException().isThrownBy(() -> event("a", " ", "L", "M"));
        assertThatIllegalArgumentException().isThrownBy(() -> event("a", "A", " ", "M"));
        assertThatIllegalArgumentException().isThrownBy(() -> event("a", "A", "L", " "));
        assertThatNullPointerException().isThrownBy(() -> new AuditEvent(
            "a", "A", null, "L", "M", null, null, null,
            AuditOutcome.SUCCESS, null, Map.of()));
        assertThatNullPointerException().isThrownBy(() -> new AuditEvent(
            "a", "A", null, "L", "M", null, null, NOW, null, null, Map.of()));

        AuditEvent emptyCompatibility = new AuditEvent();
        assertThat(emptyCompatibility.getActor()).isNull();
        assertThat(emptyCompatibility.getAction()).isNull();
        assertThat(emptyCompatibility.getLocation()).isNull();
        assertThat(emptyCompatibility.getMechanism()).isNull();
        assertThat(emptyCompatibility.getRequestId()).isNull();
        assertThat(emptyCompatibility.getOutcome()).isNull();
        assertThat(emptyCompatibility.getFailureType()).isNull();
        emptyCompatibility.setApiResponse(new AuditEvent.ApiResponse());
        assertThat(emptyCompatibility.getOutcome()).isNull();
        emptyCompatibility.setDataChange(AuditEvent.DataChange.builder()
            .fieldChanges(null)
            .build());
        assertThat(emptyCompatibility.getChanges()).isEmpty();
        assertThat(AuditEvent.fromChanges(Map.of())).isNull();
        assertThat(AuditAspect.normalize(null)).isNull();
        assertThat(AuditAspect.normalize(" ")).isNull();
        assertThat(AuditAspect.normalize("order")).isEqualTo("order");

        TestAuditTrail trail = new TestAuditTrail();
        trail.setId(1L);
        trail.setIsDeleted(true);
        trail.setCreatedBy("ada");
        trail.setCreatedAt(NOW);
        trail.setUpdatedBy("grace");
        trail.setUpdatedAt(NOW.plusHours(1));
        trail.setVersion(2L);
        assertThat(trail.getId()).isEqualTo(1L);
        assertThat(trail.getIsDeleted()).isTrue();
        assertThat(trail.getCreatedBy()).isEqualTo("ada");
        assertThat(trail.getCreatedAt()).isEqualTo(NOW);
        assertThat(trail.getUpdatedBy()).isEqualTo("grace");
        assertThat(trail.getUpdatedAt()).isEqualTo(NOW.plusHours(1));
        assertThat(trail.getVersion()).isEqualTo(2L);
        assertThat(new TestAuditTrail().getIsDeleted()).isFalse();
    }

    @Test
    void fullAuditEventCarriesAllEnterpriseAuditSections() {
        AuditEvent event = AuditEvent.builder()
            .timestamp(NOW)
            .serviceName("orders")
            .environment("prod")
            .traceId("trace")
            .spanId("span")
            .correlationId("correlation")
            .businessContext("order")
            .audit(AuditEvent.Audit.builder()
                .isAnonymousUser(false)
                .userId("42")
                .username("ada")
                .email("ada@example.com")
                .phone("0900000000")
                .roles(Set.of("ADMIN"))
                .permissions(Set.of("ORDER_WRITE"))
                .build())
            .device(AuditEvent.Device.builder()
                .deviceId("device")
                .deviceFingerprint("fingerprint")
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .browser("Browser")
                .os("OS")
                .country("VN")
                .city("HCM")
                .latitude(10.0)
                .longitude(106.0)
                .build())
            .api(AuditEvent.Api.builder()
                .entity("Order")
                .action("UPDATE")
                .businessDescription("Update order")
                .controllerClass("OrderController")
                .methodName("update")
                .apiEndpoint("/orders/1")
                .httpMethod("PATCH")
                .headers(Map.of("Content-Type", "application/json"))
                .queryParams(Map.of("verbose", "true"))
                .pathVariable("1")
                .params("safe")
                .requestBody("redacted")
                .build())
            .apiResponse(AuditEvent.ApiResponse.builder()
                .status(AuditEvent.AuditStatus.SUCCESS)
                .code("SUCCESS")
                .httpStatus(200)
                .responseBody("redacted")
                .responseSize(10L)
                .build())
            .dataChange(AuditEvent.DataChange.builder()
                .entityName("Order")
                .entityId("1")
                .oldValue("old")
                .newValue("new")
                .fieldChanges(List.of(AuditEvent.FieldChange.builder()
                    .field("status")
                    .oldValue("NEW")
                    .newValue("PAID")
                    .type(String.class.getName())
                    .build()))
                .build())
            .monitor(AuditEvent.Monitor.builder()
                .sessionId("session")
                .requestId("request")
                .executionTimeMs(10L)
                .dbTimeMs(3L)
                .externalApiTimeMs(2L)
                .success(true)
                .build())
            .security(AuditEvent.Security.builder()
                .authType("JWT")
                .clientId("client")
                .isInternalCall(false)
                .tokenId("token")
                .tokenIssuer("issuer")
                .build())
            .fraud(AuditEvent.Fraud.builder()
                .riskScore(5)
                .triggeredRules(List.of("RULE"))
                .isSuspicious(false)
                .build())
            .extra(Map.of("source", "test"))
            .build();

        assertThat(event.getServiceName()).isEqualTo("orders");
        assertThat(event.getEnvironment()).isEqualTo("prod");
        assertThat(event.getTraceId()).isEqualTo("trace");
        assertThat(event.getSpanId()).isEqualTo("span");
        assertThat(event.getAudit().getRoles()).containsExactly("ADMIN");
        assertThat(event.getDevice().getCountry()).isEqualTo("VN");
        assertThat(event.getApi().getRequestBody()).isEqualTo("redacted");
        assertThat(event.getApiResponse().getHttpStatus()).isEqualTo(200);
        assertThat(event.getDataChange().getFieldChanges()).hasSize(1);
        assertThat(event.getMonitor().getExecutionTimeMs()).isEqualTo(10L);
        assertThat(event.getSecurity().getAuthType()).isEqualTo("JWT");
        assertThat(event.getFraud().getRiskScore()).isEqualTo(5);
        assertThat(event.getExtra()).containsEntry("source", "test");
        assertThat(event.toString()).doesNotContain("redacted", "PAID");
    }

    @Test
    void auditingDateTimeProviderUsesConfiguredTimezone() {
        PlatformAuditProperties properties = new PlatformAuditProperties();
        properties.setTimezone("Asia/Ho_Chi_Minh");
        TimeProvider timeProvider = new TimeProvider() {
            public Instant nowInstant() { return NOW.toInstant(); }
            public OffsetDateTime now() { return NOW; }
            public OffsetDateTime now(ZoneId zoneId) {
                return NOW.atZoneSameInstant(zoneId).toOffsetDateTime();
            }
            public ZoneId getDefaultZone() { return ZoneOffset.UTC; }
        };

        var provider = new PlatformAuditingConfiguration()
            .platformAuditingDateTimeProvider(timeProvider, properties);

        assertThat(provider.getNow()).contains(
            NOW.atZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toOffsetDateTime()
        );
    }

    private static AuditEvent event(String actor, String action, String location, String mechanism) {
        return new AuditEvent(actor, action, null, location, mechanism, null, null,
            NOW, AuditOutcome.SUCCESS, null, Map.of());
    }

    private static AuditAspect aspect(
        ApplicationEventPublisher publisher,
        boolean publishFailures,
        RequestContextProvider request
    ) {
        PlatformAuditProperties properties = new PlatformAuditProperties();
        properties.setDefaultAuditor("system");
        properties.setPublishFailureEvents(publishFailures);
        properties.setServiceName("application");
        properties.setEnvironment("default");
        return new AuditAspect(
            () -> Optional.of("ada"),
            new DefaultAuditChangeResolver(),
            publisher,
            request,
            () -> new CurrentTraceContext("trace-1", "span-1"),
            fixedTimeProvider(),
            properties
        );
    }

    private static TimeProvider fixedTimeProvider() {
        return new TimeProvider() {
            public Instant nowInstant() { return NOW.toInstant(); }
            public OffsetDateTime now() { return NOW; }
            public OffsetDateTime now(ZoneId zoneId) {
                return NOW.atZoneSameInstant(zoneId).toOffsetDateTime();
            }
            public ZoneId getDefaultZone() { return ZoneOffset.UTC; }
        };
    }

    private static ProceedingJoinPoint joinPoint(
        Method method,
        Object[] arguments,
        Object result,
        Throwable failure
    ) throws Throwable {
        MethodSignature signature = (MethodSignature) Proxy.newProxyInstance(
            MethodSignature.class.getClassLoader(),
            new Class<?>[]{MethodSignature.class},
            (proxy, invoked, args) -> invoked.getName().equals("getMethod") ? method : null
        );
        return (ProceedingJoinPoint) Proxy.newProxyInstance(
            ProceedingJoinPoint.class.getClassLoader(),
            new Class<?>[]{ProceedingJoinPoint.class},
            (proxy, invoked, args) -> switch (invoked.getName()) {
                case "getSignature" -> signature;
                case "getArgs" -> arguments;
                case "proceed" -> {
                    if (failure != null) {
                        throw failure;
                    }
                    yield result;
                }
                default -> null;
            }
        );
    }

    private static RequestContextProvider request(String url, String method) {
        return new RequestContextProvider() {
            public String getRequestId() { return "request-1"; }
            public String getCorrelationId() { return "correlation-1"; }
            public String getRequestUrl() { return url; }
            public String getRequestMethod() { return method; }
        };
    }

    static class TestAuditTrail extends AuditTrail { }

    static final class Change implements AuditChangeSource {
        private final Map<String, Object> changes;
        Change(Map<String, Object> changes) { this.changes = changes; }
        public Map<String, Object> auditChanges() { return changes; }
    }

    static final class ChangeWithInvalidEntries implements AuditChangeSource {
        public Map<String, Object> auditChanges() {
            var map = new java.util.HashMap<String, Object>();
            map.put(null, "value");
            map.put(" ", "value");
            map.put("field", null);
            return map;
        }
    }

    static final class AuditedService {
        @Audited(action = "UPDATE", businessContext = "order", enableDiff = true)
        public String update(Change change) { return "updated"; }

        @Audited(action = "READ")
        public String read() { return "read"; }
    }
}
