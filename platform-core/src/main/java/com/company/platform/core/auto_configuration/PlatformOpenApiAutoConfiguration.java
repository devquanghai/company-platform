package com.company.platform.core.auto_configuration;

import com.company.platform.core.configuration.properties.PlatformOpenApiProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnProperty(prefix = "platform.core.openapi", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PlatformOpenApiProperties.class)
public class PlatformOpenApiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    OpenAPI platformOpenApi(PlatformOpenApiProperties properties) {
        OpenAPI openApi = new OpenAPI().info(new Info()
            .title(properties.getTitle())
            .version(properties.getVersion())
            .description(properties.getDescription()));

        SecurityScheme scheme = securityScheme(properties);
        if (scheme != null) {
            String schemeName = properties.getSecuritySchemeName();
            openApi.components(new Components().addSecuritySchemes(schemeName, scheme));
            openApi.addSecurityItem(new SecurityRequirement().addList(schemeName));
        }
        return openApi;
    }

    private static SecurityScheme securityScheme(PlatformOpenApiProperties properties) {
        return switch (properties.getAuthenticationType()) {
            case NONE -> null;
            case JWT -> new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat(properties.getBearerFormat());
            case BASIC_AUTH -> new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("basic");
            case API_KEY -> new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(properties.getApiKeyLocation())
                .name(properties.getApiKeyName());
        };
    }
}
