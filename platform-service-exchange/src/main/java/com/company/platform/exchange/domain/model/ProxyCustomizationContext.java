package com.company.platform.exchange.domain.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public final class ProxyCustomizationContext {
    private final String clientName;
    private final ExchangeProtocol protocol;
    private final String target;
}
