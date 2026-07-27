package com.company.platform.exchange.resilience.executor;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public final class ResilienceExecutionContext {
    private final String clientName;
    private final String operation;
}
