package com.company.platform.exchange.autoconfigure.properties;

import io.grpc.Status;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@FieldDefaults(level = PRIVATE)
public class RetryProperties {

    boolean enabled = true;
    int maxAttempts = 3;
    Duration waitDuration = Duration.ofMillis(300);
    boolean exponentialBackoffEnabled = true;
    double exponentialBackoffMultiplier = 2.0;
    Duration maxWaitDuration = Duration.ofSeconds(5);
    double randomizationFactor = 0.2;
    Set<Integer> retryHttpStatuses = new LinkedHashSet<>(
        Set.of(408, 425, 429, 500, 502, 503, 504));
    Set<Integer> ignoreHttpStatuses = new LinkedHashSet<>(
        Set.of(400, 401, 403, 404, 409, 422));
    Set<HttpMethod> retryMethods = new LinkedHashSet<>(
        Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS));
    Set<Status.Code> retryGrpcStatuses = new LinkedHashSet<>(
        Set.of(Status.Code.UNAVAILABLE, Status.Code.RESOURCE_EXHAUSTED,
            Status.Code.DEADLINE_EXCEEDED));
    Set<Status.Code> ignoreGrpcStatuses = new LinkedHashSet<>(
        Set.of(Status.Code.INVALID_ARGUMENT, Status.Code.UNAUTHENTICATED,
            Status.Code.PERMISSION_DENIED, Status.Code.NOT_FOUND));
    Set<String> retryExceptions = new LinkedHashSet<>(
        Set.of("java.net.ConnectException", "java.net.SocketTimeoutException"));
    boolean respectRetryAfterHeader = true;
    boolean retryNonIdempotentMethods;
}
