package com.company.platform.core.auto_configuration;

import com.company.platform.core.configuration.properties.OpenApiAuthenticationType;
import com.company.platform.core.configuration.properties.PlatformOpenApiProperties;
import com.company.platform.core.auto_configuration.PlatformOpenApiAutoConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformOpenApiAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PlatformOpenApiAutoConfiguration.class));

    @Test
    void createsMetadataWithoutClaimingSecurityByDefault() {
        runner.withPropertyValues(
                "platform.core.openapi.title=Orders API",
                "platform.core.openapi.version=2026-07",
                "platform.core.openapi.description=Order operations")
            .run(context -> {
                OpenAPI openApi = context.getBean(OpenAPI.class);
                assertThat(openApi.getInfo().getTitle()).isEqualTo("Orders API");
                assertThat(openApi.getInfo().getVersion()).isEqualTo("2026-07");
                assertThat(openApi.getInfo().getDescription()).isEqualTo("Order operations");
                assertThat(openApi.getSecurity()).isNull();
            });
    }

    @Test
    void supportsJwtBasicAndApiKeySchemesFromProperties() {
        assertScheme("JWT", SecurityScheme.Type.HTTP, "bearer", "JWT", null);
        assertScheme("BASIC_AUTH", SecurityScheme.Type.HTTP, "basic", null, null);
        assertScheme("API_KEY", SecurityScheme.Type.APIKEY, null, null, "X-TENANT-KEY");
    }

    @Test
    void bindsAllPropertiesAndBacksOff() {
        runner.withPropertyValues(
                "platform.core.openapi.authentication-type=API_KEY",
                "platform.core.openapi.security-scheme-name=tenantKey",
                "platform.core.openapi.api-key-name=X-TENANT-KEY",
                "platform.core.openapi.api-key-location=QUERY")
            .run(context -> {
                PlatformOpenApiProperties properties = context.getBean(PlatformOpenApiProperties.class);
                assertThat(properties.getAuthenticationType()).isEqualTo(OpenApiAuthenticationType.API_KEY);
                assertThat(properties.getApiKeyLocation()).isEqualTo(SecurityScheme.In.QUERY);
                properties.setEnabled(false);
                properties.setTitle("Title");
                properties.setVersion("Version");
                properties.setDescription("Description");
                properties.setAuthenticationType(OpenApiAuthenticationType.JWT);
                properties.setSecuritySchemeName("auth");
                properties.setBearerFormat("Token");
                properties.setApiKeyName("Key");
                properties.setApiKeyLocation(SecurityScheme.In.COOKIE);
                assertThat(properties.isEnabled()).isFalse();
                assertThat(properties.getBearerFormat()).isEqualTo("Token");
            });
        runner.withUserConfiguration(UserOpenApi.class)
            .run(context -> assertThat(context).hasSingleBean(OpenAPI.class));
        runner.withPropertyValues("platform.core.openapi.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(OpenAPI.class));
    }

    private void assertScheme(
        String type,
        SecurityScheme.Type expectedType,
        String expectedScheme,
        String expectedBearerFormat,
        String apiKeyName
    ) {
        String[] properties = apiKeyName == null
            ? new String[]{"platform.core.openapi.authentication-type=" + type}
            : new String[]{
                "platform.core.openapi.authentication-type=" + type,
                "platform.core.openapi.security-scheme-name=tenantKey",
                "platform.core.openapi.api-key-name=" + apiKeyName
            };
        runner.withPropertyValues(properties).run(context -> {
            PlatformOpenApiProperties configured = context.getBean(PlatformOpenApiProperties.class);
            SecurityScheme scheme = context.getBean(OpenAPI.class).getComponents()
                .getSecuritySchemes().get(configured.getSecuritySchemeName());
            assertThat(scheme.getType()).isEqualTo(expectedType);
            assertThat(scheme.getScheme()).isEqualTo(expectedScheme);
            assertThat(scheme.getBearerFormat()).isEqualTo(expectedBearerFormat);
            assertThat(scheme.getName()).isEqualTo(apiKeyName);
            assertThat(context.getBean(OpenAPI.class).getSecurity()).hasSize(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserOpenApi {
        @Bean OpenAPI customOpenApi() { return new OpenAPI(); }
    }
}
