package com.company.platform.exchange;

import com.company.platform.exchange.adapter.outbound.http.SecureUriResolver;
import com.company.platform.exchange.api.http.ExchangeRequest;
import com.company.platform.exchange.autoconfigure.properties.HttpClientProperties;
import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecureUriResolverTest {

    private final SecureUriResolver resolver = new SecureUriResolver();

    @Test
    void resolvesRelativePathVariablesAndQuery() {
        HttpClientProperties properties = new HttpClientProperties();
        properties.setBaseUrl("https://example.test/api");
        ExchangeRequest request = ExchangeRequest.builder()
            .clientName("client").method(HttpMethod.GET).path("/{id}")
            .pathVariables(Map.of("id", "a b")).queryParameters(Map.of("status", "ACTIVE"))
            .build();

        assertThat(resolver.resolve(request, properties).toString())
            .isEqualTo("https://example.test/api/a%20b?status=ACTIVE");
    }

    @Test
    void rejectsAbsoluteNetworkPathUserInfoAndFragmentByDefault() {
        HttpClientProperties properties = new HttpClientProperties();
        properties.setBaseUrl("https://example.test");

        assertRejected("https://evil.test/path", properties);
        assertRejected("//evil.test/path", properties);
        properties.setAllowAbsoluteUri(true);
        assertRejected("https://user:pass@evil.test/path", properties);
        assertRejected("https://evil.test/path#fragment", properties);
    }

    @Test
    void allowsExplicitSafeAbsoluteUri() {
        HttpClientProperties properties = new HttpClientProperties();
        properties.setBaseUrl("https://example.test");
        properties.setAllowAbsoluteUri(true);

        assertThat(resolver.resolve(request("https://other.test/path"), properties).getHost())
            .isEqualTo("other.test");
    }

    private void assertRejected(String path, HttpClientProperties properties) {
        assertThatThrownBy(() -> resolver.resolve(request(path), properties))
            .isInstanceOf(InvalidClientConfigurationException.class);
    }

    private static ExchangeRequest request(String path) {
        return ExchangeRequest.builder().clientName("client")
            .method(HttpMethod.GET).path(path).build();
    }
}
