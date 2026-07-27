package com.company.platform.exchange.domain.policy;

import com.company.platform.exchange.domain.model.ExchangeProtocol;
import io.grpc.Status;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpMethod;

@Getter
@Builder
public final class RetryContext {
    private final String clientName;
    private final ExchangeProtocol protocol;
    private final HttpMethod httpMethod;
    private final Integer httpStatus;
    private final Status.Code grpcStatus;
    private final Throwable exception;
    private final boolean idempotent;
    private final String idempotencyKey;
}
