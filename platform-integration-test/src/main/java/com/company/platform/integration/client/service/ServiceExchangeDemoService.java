package com.company.platform.integration.client.service;

import com.company.platform.exchange.api.http.ExchangeResponse;
import com.company.platform.exchange.api.http.HttpExchangeOperations;
import com.company.platform.integration.client.dto.request.ClientResourcePatchRequest;
import com.company.platform.integration.client.dto.request.ClientResourceRequest;
import com.company.platform.integration.client.dto.response.ClientCallResponse;
import com.company.platform.integration.client.dto.response.ClientResourceResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ServiceExchangeDemoService {
    private static final String RESOURCE_PATH = "/service-exchange/downstream/resources";

    private static final ParameterizedTypeReference<ClientResourceResponse> RESOURCE_TYPE =
        new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<Void> VOID_TYPE =
        new ParameterizedTypeReference<>() { };
    private final HttpExchangeOperations exchanges;
    private final String clientName;

    public ServiceExchangeDemoService(
        HttpExchangeOperations exchanges,
        @Value("${integration.service-exchange.client-name:echo}") String clientName
    ) {
        this.exchanges = exchanges;
        this.clientName = clientName;
    }

    public ClientCallResponse<ClientResourceResponse> get(
        String id, boolean includeDetails, String requestSource
    ) {
        ExchangeResponse<ClientResourceResponse> response = exchanges.get(
            clientName, RESOURCE_PATH + "/" + id,
            Map.of("includeDetails", includeDetails), headers(requestSource), RESOURCE_TYPE);
        return result("GET", response);
    }

    public ClientCallResponse<ClientResourceResponse> create(
        ClientResourceRequest body, String requestSource
    ) {
        ExchangeResponse<ClientResourceResponse> response = exchanges.post(
            clientName, RESOURCE_PATH, body, Map.of(), headers(requestSource), RESOURCE_TYPE);
        return result("POST", response);
    }

    public ClientCallResponse<ClientResourceResponse> replace(
        String id, ClientResourceRequest body, String requestSource
    ) {
        ExchangeResponse<ClientResourceResponse> response = exchanges.put(
            clientName, RESOURCE_PATH + "/" + id, body,
            Map.of(), headers(requestSource), RESOURCE_TYPE);
        return result("PUT", response);
    }

    public ClientCallResponse<ClientResourceResponse> update(
        String id, ClientResourcePatchRequest body, String requestSource
    ) {
        ExchangeResponse<ClientResourceResponse> response = exchanges.patch(
            clientName, RESOURCE_PATH + "/" + id, body,
            Map.of(), headers(requestSource), RESOURCE_TYPE);
        return result("PATCH", response);
    }

    public ClientCallResponse<Void> delete(String id, String requestSource) {
        ExchangeResponse<Void> response = exchanges.delete(
            clientName, RESOURCE_PATH + "/" + id,
            Map.of(), headers(requestSource), VOID_TYPE);
        return result("DELETE", response);
    }

    private <T> ClientCallResponse<T> result(
        String method, ExchangeResponse<T> response
    ) {
        return new ClientCallResponse<>(
            clientName, method, response.statusCode(), response.body());
    }

    private HttpHeaders headers(String requestSource) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Source", requestSource);
        return headers;
    }
}
