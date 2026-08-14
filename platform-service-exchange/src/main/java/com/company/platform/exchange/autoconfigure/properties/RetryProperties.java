package com.company.platform.exchange.autoconfigure.properties;

import io.grpc.Status;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpMethod;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/** @deprecated Configure {@code resilience4j.retry.*}. */
@Deprecated
@Getter @Setter
public class RetryProperties {
    private boolean enabled = true;
    private int maxAttempts = 3;
    private Duration waitDuration = Duration.ofMillis(300);
    private boolean exponentialBackoffEnabled = true;
    private double exponentialBackoffMultiplier = 2.0;
    private Duration maxWaitDuration = Duration.ofSeconds(5);
    private double randomizationFactor = 0.2;
    private Set<Integer> retryHttpStatuses = new LinkedHashSet<>(Set.of(408, 425, 429, 500, 502, 503, 504));
    private Set<Integer> ignoreHttpStatuses = new LinkedHashSet<>(Set.of(400, 401, 403, 404, 409, 422));
    private Set<HttpMethod> retryMethods = new LinkedHashSet<>(Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS));
    private Set<Status.Code> retryGrpcStatuses = new LinkedHashSet<>(Set.of(Status.Code.UNAVAILABLE, Status.Code.RESOURCE_EXHAUSTED, Status.Code.DEADLINE_EXCEEDED));
    private Set<Status.Code> ignoreGrpcStatuses = new LinkedHashSet<>(Set.of(Status.Code.INVALID_ARGUMENT, Status.Code.UNAUTHENTICATED, Status.Code.PERMISSION_DENIED, Status.Code.NOT_FOUND));
    private Set<String> retryExceptions = new LinkedHashSet<>(Set.of("java.net.ConnectException", "java.net.SocketTimeoutException"));
    private boolean respectRetryAfterHeader = true;
    private boolean retryNonIdempotentMethods;
}
