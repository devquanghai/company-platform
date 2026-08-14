package com.company.platform.exchange.api.customize;

import org.springframework.core.Ordered;

/** Vendor-neutral named client customization contract. */
public interface ServiceExchangeClientCustomizer extends Ordered {
    default boolean supports(String clientName) { return true; }
    void customize(ServiceExchangeClientCustomization customization);
    @Override default int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
