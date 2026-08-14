package com.company.platform.exchange.api.http;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.util.Map;

public interface HttpExchangeOperations {

    <T> ExchangeResponse<T> get(String clientName, String path, Class<T> responseType);

    <T> ExchangeResponse<T> get(
        String clientName, String path, Map<String, ?> queryParams,
        HttpHeaders headers, ParameterizedTypeReference<T> responseType);

    <T> ExchangeResponse<T> post(
        String clientName, String path, Object body, Class<T> responseType);

    default <T> ExchangeResponse<T> post(
        String clientName, String path, Object body,
        Map<String, ?> queryParams, HttpHeaders headers,
        ParameterizedTypeReference<T> responseType
    ) {
        return exchange(request(clientName, HttpMethod.POST, path, body,
            queryParams, headers, false), responseType);
    }

    <T> ExchangeResponse<T> put(
        String clientName, String path, Object body, Class<T> responseType);

    default <T> ExchangeResponse<T> put(
        String clientName, String path, Object body,
        Map<String, ?> queryParams, HttpHeaders headers,
        ParameterizedTypeReference<T> responseType
    ) {
        return exchange(request(clientName, HttpMethod.PUT, path, body,
            queryParams, headers, true), responseType);
    }

    <T> ExchangeResponse<T> patch(
        String clientName, String path, Object body, Class<T> responseType);

    default <T> ExchangeResponse<T> patch(
        String clientName, String path, Object body,
        Map<String, ?> queryParams, HttpHeaders headers,
        ParameterizedTypeReference<T> responseType
    ) {
        return exchange(request(clientName, HttpMethod.PATCH, path, body,
            queryParams, headers, false), responseType);
    }

    <T> ExchangeResponse<T> delete(String clientName, String path, Class<T> responseType);

    default <T> ExchangeResponse<T> delete(
        String clientName, String path, Map<String, ?> queryParams,
        HttpHeaders headers, ParameterizedTypeReference<T> responseType
    ) {
        return exchange(request(clientName, HttpMethod.DELETE, path, null,
            queryParams, headers, true), responseType);
    }

    <T> ExchangeResponse<T> exchange(
        ExchangeRequest request, ParameterizedTypeReference<T> responseType);

    private static ExchangeRequest request(
        String clientName, HttpMethod method, String path, Object body,
        Map<String, ?> queryParams, HttpHeaders headers, boolean idempotent
    ) {
        return ExchangeRequest.builder()
            .clientName(clientName)
            .method(method)
            .path(path)
            .body(body)
            .queryParameters(queryParams)
            .headers(headers)
            .idempotent(idempotent)
            .build();
    }
}
