package com.company.platform.integration.client.dto.response;

public record ClientCallResponse<T>(
    String clientName,
    String operation,
    int downstreamStatus,
    T body
) { }
