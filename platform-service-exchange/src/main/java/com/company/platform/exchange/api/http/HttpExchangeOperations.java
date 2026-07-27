package com.company.platform.exchange.api.http;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;

import java.util.Map;

public interface HttpExchangeOperations {

    <T> ExchangeResponse<T> get(String clientName, String path, Class<T> responseType);

    <T> ExchangeResponse<T> get(
        String clientName, String path, Map<String, ?> queryParams,
        HttpHeaders headers, ParameterizedTypeReference<T> responseType);

    <T> ExchangeResponse<T> post(
        String clientName, String path, Object body, Class<T> responseType);

    <T> ExchangeResponse<T> put(
        String clientName, String path, Object body, Class<T> responseType);

    <T> ExchangeResponse<T> patch(
        String clientName, String path, Object body, Class<T> responseType);

    <T> ExchangeResponse<T> delete(String clientName, String path, Class<T> responseType);

    <T> ExchangeResponse<T> exchange(
        ExchangeRequest request, ParameterizedTypeReference<T> responseType);
}
