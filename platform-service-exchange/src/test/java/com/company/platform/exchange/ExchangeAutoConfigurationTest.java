package com.company.platform.exchange;

import com.company.platform.core.auto_configuration.PlatformCoreAutoConfiguration;
import com.company.platform.core.auto_configuration.PlatformJacksonAutoConfiguration;
import com.company.platform.exchange.api.http.HttpExchangeOperations;
import com.company.platform.exchange.api.http.ExchangeRequest;
import com.company.platform.exchange.api.http.ExchangeResponse;
import com.company.platform.exchange.application.service.ClientConfigurationResolver;
import com.company.platform.exchange.autoconfigure.PlatformServiceExchangeAutoConfiguration;
import com.company.platform.exchange.autoconfigure.audit.ExchangeAuditAutoConfiguration;
import com.company.platform.exchange.autoconfigure.http.HttpExchangeAutoConfiguration;
import com.company.platform.exchange.autoconfigure.observability.ExchangeObservabilityAutoConfiguration;
import com.company.platform.exchange.autoconfigure.properties.ServiceExchangeProperties;
import com.company.platform.exchange.autoconfigure.resilience.ExchangeResilienceAutoConfiguration;
import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            PlatformCoreAutoConfiguration.class,
            PlatformJacksonAutoConfiguration.class,
            PlatformServiceExchangeAutoConfiguration.class,
            ExchangeAuditAutoConfiguration.class,
            ExchangeResilienceAutoConfiguration.class,
            ExchangeObservabilityAutoConfiguration.class,
            HttpExchangeAutoConfiguration.class))
        .withUserConfiguration(TestInfrastructureConfiguration.class);

    @Test
    void globalDisableCreatesNoExchangeBeans() {
        runner.withPropertyValues("platform.service-exchange.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(ServiceExchangeProperties.class);
                assertThat(context).doesNotHaveBean(ClientConfigurationResolver.class);
                assertThat(context).doesNotHaveBean(HttpExchangeOperations.class);
            });
    }

    @Test
    void validNamedHttpClientCreatesOperationsAndBindsProperties() {
        runner.withPropertyValues(
            "platform.service-exchange.clients.payment.enabled=true",
            "platform.service-exchange.clients.payment.protocol=HTTP",
            "platform.service-exchange.clients.payment.http.base-url=http://localhost:8080")
            .run(context -> {
                assertThat(context).hasSingleBean(HttpExchangeOperations.class);
                assertThat(context).hasSingleBean(ClientConfigurationResolver.class);
                assertThat(context.getBean(ServiceExchangeProperties.class)
                    .getClients()).containsKey("payment");
            });
    }

    @Test
    void customOperationsBeanWins() {
        runner.withUserConfiguration(CustomOperationsConfiguration.class)
            .withPropertyValues(
                "platform.service-exchange.clients.payment.protocol=HTTP",
                "platform.service-exchange.clients.payment.http.base-url=http://localhost:8080")
            .run(context -> assertThat(context.getBean(HttpExchangeOperations.class))
                .isSameAs(context.getBean("customOperations")));
    }

    @Test
    void invalidProtocolSpecificConfigurationFailsFast() {
        runner.withPropertyValues(
            "platform.service-exchange.clients.broken.protocol=HTTP")
            .run(context -> assertThat(context.getStartupFailure())
                .hasRootCauseInstanceOf(InvalidClientConfigurationException.class));
    }

    @Test
    void registersImportsAndVietnameseMetadata() throws IOException {
        ClassLoader loader = getClass().getClassLoader();
        String imports = new String(loader.getResourceAsStream(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
            .readAllBytes(), StandardCharsets.UTF_8);
        String metadata = new String(loader.getResourceAsStream(
            "META-INF/additional-spring-configuration-metadata.json")
            .readAllBytes(), StandardCharsets.UTF_8);

        assertThat(imports).contains(
            PlatformServiceExchangeAutoConfiguration.class.getName(),
            HttpExchangeAutoConfiguration.class.getName());
        assertThat(metadata).contains(
            "platform.service-exchange.enabled",
            "Bật hoặc tắt toàn bộ auto-configuration",
            "\"defaultValue\": true");
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomOperationsConfiguration {
        @Bean
        HttpExchangeOperations customOperations() {
            return new UnsupportedOperations();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestInfrastructureConfiguration {
        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }
    }

    static final class UnsupportedOperations implements HttpExchangeOperations {
        @Override public <T> ExchangeResponse<T> get(
            String clientName, String path, Class<T> responseType) { return null; }
        @Override public <T> ExchangeResponse<T> get(
            String clientName, String path, Map<String, ?> queryParams,
            HttpHeaders headers, ParameterizedTypeReference<T> responseType) { return null; }
        @Override public <T> ExchangeResponse<T> post(
            String clientName, String path, Object body, Class<T> responseType) { return null; }
        @Override public <T> ExchangeResponse<T> put(
            String clientName, String path, Object body, Class<T> responseType) { return null; }
        @Override public <T> ExchangeResponse<T> patch(
            String clientName, String path, Object body, Class<T> responseType) { return null; }
        @Override public <T> ExchangeResponse<T> delete(
            String clientName, String path, Class<T> responseType) { return null; }
        @Override public <T> ExchangeResponse<T> exchange(
            ExchangeRequest request, ParameterizedTypeReference<T> responseType) { return null; }
    }
}
