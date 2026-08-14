package com.company.platform.exchange.observability.logging;

import com.company.platform.exchange.api.http.ExchangeRequest;

import java.net.URI;

public interface CurlGenerator {
    String generate(ExchangeRequest request, URI target, int maxBodyLength);

    default String generate(
        ExchangeRequest request, URI target, int maxBodyLength, boolean includeBody
    ) {
        return generate(request, target, maxBodyLength);
    }

    default String generate(
        ExchangeRequest request, URI target, int maxBodyLength,
        boolean includeHeaders, boolean includeBody
    ) {
        return generate(request, target, maxBodyLength, includeBody);
    }
}
