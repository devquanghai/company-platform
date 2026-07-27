package com.company.platform.exchange.api.http;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public final class OutboundError {
    private final String code;
    private final String category;
    private final String message;
    private final Integer status;
    private final boolean retryable;
}
