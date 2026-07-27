package com.company.platform.exchange.observability.logging;

import com.company.platform.exchange.api.http.ExchangeRequest;

import java.net.URI;

public interface CurlGenerator {
    String generate(ExchangeRequest request, URI target, int maxBodyLength);
}
