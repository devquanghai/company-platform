package com.company.platform.exchange.observability.internal.adapter.logging;

import com.company.platform.exchange.observability.logging.CurlGenerator;
import com.company.platform.exchange.observability.logging.OutboundDataMasker;

import com.company.platform.exchange.api.http.ExchangeRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.net.URI;

public final class DefaultCurlGenerator implements CurlGenerator {

    private final OutboundDataMasker masker;

    public DefaultCurlGenerator(OutboundDataMasker masker) {
        this.masker = masker;
    }

    @Override
    public String generate(ExchangeRequest request, URI target, int maxBodyLength) {
        return generate(request, target, maxBodyLength, false);
    }

    @Override
    public String generate(
        ExchangeRequest request, URI target, int maxBodyLength, boolean includeBody
    ) {
        return generate(request, target, maxBodyLength, false, includeBody);
    }

    @Override
    public String generate(
        ExchangeRequest request, URI target, int maxBodyLength,
        boolean includeHeaders, boolean includeBody
    ) {
        StringBuilder command = new StringBuilder("curl --request ")
            .append(shell(request.getMethod().name()))
            .append(" --url ").append(shell(masker.maskUri(target).toString()));
        if (includeHeaders) {
            HttpHeaders headers = masker.maskHeaders(request.getHeaders());
            headers.forEach((name, values) -> values.forEach(value ->
                command.append(" --header ").append(shell(name + ": " + value))));
        }
        if (includeBody && safeBody(request)) {
            command.append(" --data ")
                .append(shell(masker.maskBody(request.getBody(), maxBodyLength)));
        }
        return command.toString();
    }

    private boolean safeBody(ExchangeRequest request) {
        Object body = request.getBody();
        MediaType type = request.getContentType();
        if (body == null || body instanceof byte[] || body instanceof Resource
            || body instanceof InputStream || body instanceof Publisher<?>) {
            return false;
        }
        return type == null || MediaType.APPLICATION_JSON.includes(type)
            || "text".equalsIgnoreCase(type.getType());
    }

    static String shell(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
