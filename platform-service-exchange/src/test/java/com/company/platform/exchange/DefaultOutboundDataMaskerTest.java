package com.company.platform.exchange;

import com.company.platform.core.json.JsonMapperHelper;
import com.company.platform.exchange.api.http.ExchangeRequest;
import com.company.platform.exchange.observability.logging.DefaultCurlGenerator;
import com.company.platform.exchange.observability.logging.DefaultOutboundDataMasker;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultOutboundDataMaskerTest {

    private final DefaultOutboundDataMasker masker = new DefaultOutboundDataMasker(
        new JsonMapperHelper(JsonMapper.builder().build()),
        Set.of("X-Custom-Secret"), Set.of("customerId"));

    @Test
    void masksHeadersUriJsonAttributesAndTruncates() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("raw-token");
        headers.set("X-Custom-Secret", "raw");
        headers.set("X-Safe", "visible");

        assertThat(masker.maskHeaders(headers).getFirst(HttpHeaders.AUTHORIZATION))
            .isEqualTo("***");
        assertThat(masker.maskHeaders(headers).getFirst("X-Custom-Secret")).isEqualTo("***");
        assertThat(masker.maskHeaders(headers).getFirst("X-Safe")).isEqualTo("visible");
        assertThat(masker.maskUri(
            URI.create("https://example.test/a?token=raw&name=ok&customerId=42")).toString())
            .contains("token=***", "customerId=***", "name=ok");
        assertThat(masker.maskBody("{\"password\":\"raw\",\"name\":\"ok\"}", 200))
            .isEqualTo("{\"password\":\"***\",\"name\":\"ok\"}");
        assertThat(masker.maskBody("123456", 3)).isEqualTo("123...<truncated>");
        assertThat(masker.maskAttributes(Map.of("token", "raw", "safe", 1)))
            .containsEntry("token", "***").containsEntry("safe", 1);
    }

    @Test
    void refusesBinaryAndSerializesObjects() {
        assertThat(masker.maskBody("x".getBytes(StandardCharsets.UTF_8), 100))
            .isEqualTo("<binary-or-stream-body-not-logged>");
        assertThat(masker.maskBody(Map.of("secret", "raw"), 100)).contains("***");
        assertThat(masker.maskBody(null, 100)).isEmpty();
        assertThat(masker.maskBody("ok", -1)).isEqualTo("ok");
    }

    @Test
    void curlUsesSafeShellQuotingAndMaskedData() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("raw");
        headers.set("X-Name", "a'b");
        ExchangeRequest request = ExchangeRequest.builder()
            .clientName("test").method(HttpMethod.POST).path("/pay")
            .headers(headers).body("{\"password\":\"a'b\"}").build();

        String command = new DefaultCurlGenerator(masker).generate(
            request, URI.create("https://example.test/pay?token=raw"), 100);

        assertThat(command).contains("curl --request 'POST'", "Authorization: ***",
            "token=***", "'\"'\"'").doesNotContain("raw");
    }
}
