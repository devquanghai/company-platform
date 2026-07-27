package com.company.platform.exchange;

import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.autoconfigure.properties.ServiceExchangeProperties;
import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.support.ServiceExchangePropertiesValidator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceExchangePropertiesValidatorTest {

    @Test
    void acceptsValidHttpAndGrpcClients() {
        ServiceExchangeProperties properties = new ServiceExchangeProperties();
        ClientProperties http = client(ExchangeProtocol.HTTP);
        http.getHttp().setBaseUrl("https://example.test");
        ClientProperties grpc = client(ExchangeProtocol.GRPC);
        grpc.getGrpc().setAddress("dns:///example.test:9090");
        properties.getClients().put("http-client", http);
        properties.getClients().put("grpc-client", grpc);

        assertThatCode(() -> validator(properties).validate()).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidNamesProtocolTargetsTimeoutProxyRetryRateAndInsecureSsl() {
        assertInvalid("", client(ExchangeProtocol.HTTP));
        assertInvalid("INVALID NAME", client(ExchangeProtocol.HTTP));
        assertInvalid("missing-protocol", new ClientProperties());

        ClientProperties http = client(ExchangeProtocol.HTTP);
        assertInvalid("http", http);
        http.getHttp().setBaseUrl("https://example.test");
        http.getHttp().setConnectTimeout(Duration.ZERO);
        assertInvalid("http", http);

        ClientProperties grpc = client(ExchangeProtocol.GRPC);
        assertInvalid("grpc", grpc);
        grpc.getGrpc().setAddress("dns:///example:9090");
        grpc.getProxy().setEnabled(true);
        assertInvalid("grpc", grpc);
        grpc.getProxy().setHost("proxy");
        grpc.getProxy().setPort(8080);
        grpc.getResilience().getRetry().setMaxAttempts(0);
        assertInvalid("grpc", grpc);
        grpc.getResilience().getRetry().setMaxAttempts(1);
        grpc.getResilience().getRateLimiter().setLimitForPeriod(0);
        assertInvalid("grpc", grpc);

        grpc.getResilience().getRateLimiter().setLimitForPeriod(1);
        grpc.getSsl().setEnabled(true);
        grpc.getSsl().setTrustAll(true);
        assertInvalid("grpc", grpc);
    }

    @Test
    void allowsTrustAllOnlyWithExplicitNonProductionOverride() {
        ServiceExchangeProperties properties = new ServiceExchangeProperties();
        properties.setAllowInsecureSsl(true);
        ClientProperties client = client(ExchangeProtocol.HTTP);
        client.getHttp().setBaseUrl("https://example.test");
        client.getSsl().setEnabled(true);
        client.getSsl().setTrustAll(true);
        properties.getClients().put("local-client", client);

        assertThatCode(() -> validator(properties).validate()).doesNotThrowAnyException();
        properties.setEnvironment("production");
        assertThatThrownBy(() -> validator(properties).validate())
            .isInstanceOf(InvalidClientConfigurationException.class);
    }

    private static ClientProperties client(ExchangeProtocol protocol) {
        ClientProperties value = new ClientProperties();
        value.setProtocol(protocol);
        return value;
    }

    private static void assertInvalid(String name, ClientProperties client) {
        ServiceExchangeProperties properties = new ServiceExchangeProperties();
        properties.getClients().put(name, client);
        assertThatThrownBy(() -> validator(properties).validate())
            .isInstanceOf(InvalidClientConfigurationException.class);
    }

    private static ServiceExchangePropertiesValidator validator(
        ServiceExchangeProperties properties
    ) {
        return new ServiceExchangePropertiesValidator(properties, Optional.empty());
    }
}
