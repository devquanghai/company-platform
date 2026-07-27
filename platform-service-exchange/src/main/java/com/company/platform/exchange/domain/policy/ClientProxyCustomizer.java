package com.company.platform.exchange.domain.policy;

import com.company.platform.exchange.domain.model.ProxyCustomizationContext;
import com.company.platform.exchange.domain.model.ProxyEndpoint;

public interface ClientProxyCustomizer {
    ProxyEndpoint customize(ProxyCustomizationContext context, ProxyEndpoint configured);
}
