package com.company.platform.exchange.api.customize;

import com.company.platform.exchange.api.client.ServiceExchangeClientType;

import java.util.function.Supplier;

public interface ServiceExchangeClientCustomization {
    String clientName();
    ServiceExchangeClientType clientType();
    void defaultHeader(String name, Supplier<String> value);
}
