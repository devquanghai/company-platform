package com.company.platform.exchange.api.resilience;

import com.company.platform.exchange.domain.exception.OutboundGrpcException;
import com.company.platform.exchange.domain.exception.OutboundHttpException;
import com.company.platform.exchange.domain.exception.OutboundTimeoutException;
import com.company.platform.exchange.api.exception.ServiceExchangeClientException;

import java.util.function.Predicate;

public final class OutboundRetryPredicate implements Predicate<Throwable> {
    @Override
    public boolean test(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof OutboundHttpException http) {
                return http.isRetryable();
            }
            if (current instanceof OutboundGrpcException grpc) {
                return grpc.isRetryable();
            }
            if (current instanceof OutboundTimeoutException) {
                return true;
            }
            if (current instanceof ServiceExchangeClientException clientFailure) {
                return clientFailure.retryable();
            }
            current = current.getCause();
        }
        return false;
    }
}
