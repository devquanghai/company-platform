package com.company.platform.exchange.api.resilience;

import com.company.platform.exchange.api.exception.ServiceExchangeClientException;
import com.company.platform.exchange.domain.exception.OutboundGrpcException;
import com.company.platform.exchange.domain.exception.OutboundHttpException;
import com.company.platform.exchange.domain.exception.OutboundTimeoutException;

import java.util.function.Predicate;
import java.util.concurrent.TimeoutException;

/** Records only remote infrastructure and server-side failures. */
public final class OutboundCircuitBreakerPredicate implements Predicate<Throwable> {
    @Override
    public boolean test(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof OutboundHttpException http) {
                Integer status = http.getStatus();
                return http.isRecordable();
            }
            if (current instanceof OutboundGrpcException grpc) {
                return grpc.getStatus() == io.grpc.Status.Code.UNAVAILABLE
                    || grpc.getStatus() == io.grpc.Status.Code.RESOURCE_EXHAUSTED
                    || grpc.getStatus() == io.grpc.Status.Code.DEADLINE_EXCEEDED;
            }
            if (current instanceof OutboundTimeoutException
                || current instanceof TimeoutException) {
                return true;
            }
            if (current instanceof ServiceExchangeClientException clientFailure) {
                return clientFailure.recordable();
            }
            current = current.getCause();
        }
        return false;
    }
}
