package com.company.platform.exchange.api.http;

import org.springframework.web.client.RestClient;

public interface HttpClientRegistry {
    RestClient getClient(String clientName);
}
