package com.company.platform.core.audit;

import com.company.platform.core.configuration.properties.PlatformAuditProperties;
import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.time.TimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import com.company.platform.core.trace.TraceContextProvider;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.AuditorAware;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;

/** Publishes structured audit events around explicitly annotated business operations. */
@Slf4j
@Aspect
public final class AuditAspect {

    private final AuditorAware<String> auditorAware;
    private final AuditChangeResolver changeResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final RequestContextProvider requestContextProvider;
    private final TraceContextProvider traceContextProvider;
    private final TimeProvider timeProvider;
    private final PlatformAuditProperties properties;

    public AuditAspect(
        AuditorAware<String> auditorAware,
        AuditChangeResolver changeResolver,
        ApplicationEventPublisher eventPublisher,
        RequestContextProvider requestContextProvider,
        TraceContextProvider traceContextProvider,
        TimeProvider timeProvider,
        PlatformAuditProperties properties
    ) {
        this.auditorAware = Objects.requireNonNull(auditorAware, "auditorAware must not be null");
        this.changeResolver = Objects.requireNonNull(changeResolver, "changeResolver must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.requestContextProvider = Objects.requireNonNull(
            requestContextProvider,
            "requestContextProvider must not be null"
        );
        this.traceContextProvider = Objects.requireNonNull(
            traceContextProvider,
            "traceContextProvider must not be null"
        );
        this.timeProvider = Objects.requireNonNull(timeProvider, "timeProvider must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * Constructor tương thích với integration cũ chưa cung cấp trace provider.
     */
    public AuditAspect(
        AuditorAware<String> auditorAware,
        AuditChangeResolver changeResolver,
        ApplicationEventPublisher eventPublisher,
        RequestContextProvider requestContextProvider,
        TimeProvider timeProvider,
        PlatformAuditProperties properties
    ) {
        this(
            auditorAware,
            changeResolver,
            eventPublisher,
            requestContextProvider,
            CurrentTraceContext::empty,
            timeProvider,
            properties
        );
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        long startedAt = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            publish(joinPoint, method, audited, result, AuditOutcome.SUCCESS, null, startedAt);
            return result;
        } catch (Throwable failure) {
            if (properties.isPublishFailureEvents()) {
                publish(joinPoint, method, audited, null, AuditOutcome.FAILURE,
                    failure.getClass().getName(), startedAt);
            }
            throw failure;
        }
    }

    @SuppressWarnings("deprecation")
    private void publish(
        ProceedingJoinPoint joinPoint,
        Method method,
        Audited audited,
        Object result,
        AuditOutcome outcome,
        String failureType,
        long startedAt
    ) {
        try {
            Map<String, Object> changes = audited.enableDiff()
                ? changeResolver.resolve(method, joinPoint.getArgs(), result)
                : Map.of();
            String methodLocation = method.getDeclaringClass().getName() + "#" + method.getName();
            String requestUrl = requestContextProvider.getRequestUrl();
            String actor = auditorAware.getCurrentAuditor().orElse(properties.getDefaultAuditor());
            String businessContext = valueOrDefault(
                audited.businessContext(),
                audited.resource()
            );
            CurrentTraceContext trace = traceContextProvider.getCurrentContext();
            eventPublisher.publishEvent(AuditEvent.builder()
                .timestamp(timeProvider.now())
//                .serviceName(properties.getServiceName())
//                .environment(properties.getEnvironment())
                .traceId(trace.getTraceId())
                .spanId(trace.getSpanId())
                .correlationId(requestContextProvider.getCorrelationId())
                .businessContext(normalize(businessContext))
                .audit(AuditEvent.Audit.builder()
                    .isAnonymousUser(false)
                    .username(actor)
                    .build())
                .device(AuditEvent.Device.builder()
                    .ipAddress(requestContextProvider.getRemoteAddress())
                    .userAgent(requestContextProvider.getUserAgent())
                    .build())
                .api(AuditEvent.Api.builder()
                    .entity(normalize(businessContext))
                    .action(audited.action())
                    .businessDescription(normalize(businessContext))
                    .controllerClass(method.getDeclaringClass().getName())
                    .methodName(method.getName())
                    .apiEndpoint(requestUrl == null ? methodLocation : requestUrl)
                    .httpMethod(valueOrDefault(requestContextProvider.getRequestMethod(), "JAVA"))
                    .build())
                .apiResponse(AuditEvent.ApiResponse.builder()
                    .status(outcome == AuditOutcome.SUCCESS
                        ? AuditEvent.AuditStatus.SUCCESS
                        : AuditEvent.AuditStatus.FAILED)
                    .code(failureType)
                    .errorMessage(failureType)
                    .build())
                .dataChange(AuditEvent.fromChanges(changes))
                .monitor(AuditEvent.Monitor.builder()
                    .requestId(requestContextProvider.getRequestId())
                    .executionTimeMs((System.nanoTime() - startedAt) / 1_000_000)
                    .success(outcome == AuditOutcome.SUCCESS)
                    .build())
                .build());
        } catch (RuntimeException auditFailure) {
            log.warn("Audit event publication failed for {}#{}",
                method.getDeclaringClass().getName(), method.getName(), auditFailure);
        }
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
