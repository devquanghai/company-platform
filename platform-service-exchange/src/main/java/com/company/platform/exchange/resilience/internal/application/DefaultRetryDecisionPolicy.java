package com.company.platform.exchange.resilience.internal.application;

import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.domain.policy.RetryContext;
import com.company.platform.exchange.domain.policy.RetryDecision;
import com.company.platform.exchange.domain.policy.RetryDecisionPolicy;
import io.grpc.Status;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeoutException;

public final class DefaultRetryDecisionPolicy implements RetryDecisionPolicy {
    private static final Set<Integer> RETRYABLE_HTTP =
        Set.of(408, 502, 503, 504);
    private static final Set<Status.Code> RETRYABLE_GRPC = Set.of(
        Status.Code.UNAVAILABLE,
        Status.Code.RESOURCE_EXHAUSTED,
        Status.Code.DEADLINE_EXCEEDED);

    @Override
    public RetryDecision evaluate(RetryContext context) {
        if (!isSafeInvocation(context)) {
            return RetryDecision.doNotRetry("request is not safely idempotent");
        }
        if (context.getProtocol() == ExchangeProtocol.HTTP
            && context.getHttpStatus() != null) {
            return RETRYABLE_HTTP.contains(context.getHttpStatus())
                ? retry("transient HTTP status")
                : RetryDecision.doNotRetry("non-retryable HTTP status");
        }
        if (context.getProtocol() == ExchangeProtocol.GRPC
            && context.getGrpcStatus() != null) {
            return RETRYABLE_GRPC.contains(context.getGrpcStatus())
                ? retry("transient gRPC status")
                : RetryDecision.doNotRetry("non-retryable gRPC status");
        }
        return isConnectionFailure(context.getException())
            ? retry("connection or timeout failure")
            : RetryDecision.doNotRetry("non-retryable exception");
    }

    private boolean isSafeInvocation(RetryContext context) {
        if (context.getProtocol() == ExchangeProtocol.GRPC) {
            return context.isIdempotent();
        }
        HttpMethod method = context.getHttpMethod();
        if (method == HttpMethod.GET || method == HttpMethod.HEAD
            || method == HttpMethod.OPTIONS) {
            return true;
        }
        return context.isIdempotent() || StringUtils.hasText(context.getIdempotencyKey());
    }

    private boolean isConnectionFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof IOException || current instanceof TimeoutException
                || current instanceof org.springframework.web.client.ResourceAccessException
                || current instanceof org.springframework.web.reactive.function.client.WebClientRequestException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private RetryDecision retry(String reason) {
        return RetryDecision.retry(Duration.ZERO, reason);
    }
}
