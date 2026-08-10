package com.company.platform.exchange.resilience.internal.application;

import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.autoconfigure.properties.RetryProperties;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.domain.policy.RetryContext;
import com.company.platform.exchange.domain.policy.RetryDecision;
import com.company.platform.exchange.domain.policy.RetryDecisionPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;

public final class DefaultRetryDecisionPolicy implements RetryDecisionPolicy {

    private final ClientConfigurationResolver resolver;

    public DefaultRetryDecisionPolicy(ClientConfigurationResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public RetryDecision evaluate(RetryContext context) {
        RetryProperties retry = resolver.resolve(context.getClientName())
            .getResilience().getRetry();
        if (!retry.isEnabled()) {
            return RetryDecision.doNotRetry("retry disabled");
        }
        if (context.getProtocol() == ExchangeProtocol.HTTP) {
            return evaluateHttp(context, retry);
        }
        if (context.getGrpcStatus() != null
            && retry.getIgnoreGrpcStatuses().contains(context.getGrpcStatus())) {
            return RetryDecision.doNotRetry("ignored gRPC status");
        }
        if (context.getGrpcStatus() != null
            && retry.getRetryGrpcStatuses().contains(context.getGrpcStatus())
            && context.isIdempotent()) {
            return RetryDecision.retry(retry.getWaitDuration(), "configured gRPC status");
        }
        return retryException(context, retry);
    }

    private static RetryDecision evaluateHttp(RetryContext context, RetryProperties retry) {
        if (context.getHttpStatus() != null
            && retry.getIgnoreHttpStatuses().contains(context.getHttpStatus())) {
            return RetryDecision.doNotRetry("ignored HTTP status");
        }
        if (!methodRetryable(context, retry)) {
            return RetryDecision.doNotRetry("HTTP method is not safely retryable");
        }
        if (context.getHttpStatus() != null
            && retry.getRetryHttpStatuses().contains(context.getHttpStatus())) {
            return RetryDecision.retry(retry.getWaitDuration(), "configured HTTP status");
        }
        return retryException(context, retry);
    }

    private static boolean methodRetryable(RetryContext context, RetryProperties retry) {
        HttpMethod method = context.getHttpMethod();
        if (method == null) {
            return false;
        }
        boolean explicitlyIdempotent = context.isIdempotent()
            || StringUtils.hasText(context.getIdempotencyKey());
        if (method == HttpMethod.POST || method == HttpMethod.PATCH) {
            return explicitlyIdempotent || retry.isRetryNonIdempotentMethods();
        }
        return retry.getRetryMethods().contains(method)
            || (explicitlyIdempotent && (method == HttpMethod.PUT || method == HttpMethod.DELETE));
    }

    private static RetryDecision retryException(
        RetryContext context, RetryProperties retry
    ) {
        Throwable exception = context.getException();
        while (exception != null) {
            if (retry.getRetryExceptions().contains(exception.getClass().getName())) {
                return RetryDecision.retry(retry.getWaitDuration(), "configured exception");
            }
            exception = exception.getCause();
        }
        return RetryDecision.doNotRetry("non-retryable failure");
    }
}
