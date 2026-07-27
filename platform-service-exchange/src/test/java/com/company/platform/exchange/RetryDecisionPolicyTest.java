package com.company.platform.exchange;

import com.company.platform.exchange.application.service.ClientConfigurationResolver;
import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.autoconfigure.properties.ServiceExchangeProperties;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.domain.policy.RetryContext;
import com.company.platform.exchange.resilience.executor.DefaultRetryDecisionPolicy;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;

class RetryDecisionPolicyTest {

    private DefaultRetryDecisionPolicy policy;

    @BeforeEach
    void setUp() {
        ServiceExchangeProperties properties = new ServiceExchangeProperties();
        ClientProperties client = new ClientProperties();
        client.setProtocol(ExchangeProtocol.HTTP);
        client.getHttp().setBaseUrl("https://example.test");
        properties.getClients().put("client", client);
        policy = new DefaultRetryDecisionPolicy(new ClientConfigurationResolver(properties));
    }

    @Test
    void retriesConfiguredSafeHttpStatusAndException() {
        assertThat(policy.evaluate(http(HttpMethod.GET, 503, null, false, null)).retry()).isTrue();
        assertThat(policy.evaluate(
            http(HttpMethod.GET, null, new ConnectException(), false, null)).retry()).isTrue();
    }

    @Test
    void ignoresBusinessStatusAndUnsafeMethodsWithoutIdempotency() {
        assertThat(policy.evaluate(http(HttpMethod.GET, 404, null, false, null)).retry()).isFalse();
        assertThat(policy.evaluate(http(HttpMethod.POST, 503, null, false, null)).retry()).isFalse();
        assertThat(policy.evaluate(
            http(HttpMethod.POST, 503, null, false, "key")).retry()).isTrue();
        assertThat(policy.evaluate(http(HttpMethod.PATCH, 503, null, true, null)).retry()).isTrue();
    }

    @Test
    void handlesGrpcStatusAndDisabledRetry() {
        assertThat(policy.evaluate(RetryContext.builder().clientName("client")
            .protocol(ExchangeProtocol.GRPC).grpcStatus(Status.Code.UNAVAILABLE)
            .idempotent(true).build()).retry()).isTrue();
        assertThat(policy.evaluate(RetryContext.builder().clientName("client")
            .protocol(ExchangeProtocol.GRPC).grpcStatus(Status.Code.NOT_FOUND)
            .idempotent(true).build()).retry()).isFalse();

        ServiceExchangeProperties properties = new ServiceExchangeProperties();
        ClientProperties disabled = new ClientProperties();
        disabled.setProtocol(ExchangeProtocol.HTTP);
        disabled.getResilience().getRetry().setEnabled(false);
        properties.getClients().put("disabled", disabled);
        DefaultRetryDecisionPolicy disabledPolicy =
            new DefaultRetryDecisionPolicy(new ClientConfigurationResolver(properties));
        assertThat(disabledPolicy.evaluate(RetryContext.builder().clientName("disabled")
            .protocol(ExchangeProtocol.HTTP).httpMethod(HttpMethod.GET)
            .httpStatus(503).build()).retry()).isFalse();
    }

    private static RetryContext http(
        HttpMethod method, Integer status, Throwable exception,
        boolean idempotent, String key
    ) {
        return RetryContext.builder().clientName("client").protocol(ExchangeProtocol.HTTP)
            .httpMethod(method).httpStatus(status).exception(exception)
            .idempotent(idempotent).idempotencyKey(key).build();
    }
}
