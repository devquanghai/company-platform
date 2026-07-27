package com.company.platform.exchange.observability.logging;

import com.company.platform.exchange.api.http.ExchangeRequest;
import org.springframework.http.HttpHeaders;

import java.net.URI;

public final class DefaultCurlGenerator implements CurlGenerator {

    private final OutboundDataMasker masker;

    public DefaultCurlGenerator(OutboundDataMasker masker) {
        this.masker = masker;
    }

    @Override
    public String generate(ExchangeRequest request, URI target, int maxBodyLength) {
        StringBuilder command = new StringBuilder("curl --request ")
            .append(shell(request.getMethod().name()))
            .append(" --url ").append(shell(masker.maskUri(target).toString()));
        HttpHeaders headers = masker.maskHeaders(request.getHeaders());
        headers.forEach((name, values) -> values.forEach(value ->
            command.append(" --header ").append(shell(name + ": " + value))));
        if (request.getBody() != null) {
            command.append(" --data ")
                .append(shell(masker.maskBody(request.getBody(), maxBodyLength)));
        }
        return command.toString();
    }

    static String shell(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
