package com.company.platform.core.auto_configuration;

import com.company.platform.core.configuration.properties.OpenApiAuthenticationType;
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
    private static final String JWT_SCHEME = "jwt";
    private static final String APIKEY_SCHEMA = "api-key";
    private static final String BASIC_AUTH_SCHEME = "basic";
    private static final String BEARER_FORMAT = "JWT";


    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    OpenAPI platformOpenApi(PlatformOpenApiProperties properties) {
        OpenAPI openApi = new OpenAPI().info(new Info()
            .title(properties.getTitle())
            .version(properties.getVersion())
            .description(properties.getDescription()));

        SecurityScheme scheme = securityScheme(properties);
        if (properties.getAuthenticationType().equals(OpenApiAuthenticationType.CLIENT_ID_SECRET)) {
            this.addSchemaClientId(openApi);
        } else {
            if (scheme != null) {
                openApi.components(new Components().addSecuritySchemes(scheme.getScheme(), scheme));
                openApi.addSecurityItem(new SecurityRequirement().addList(scheme.getScheme()));
            }
        }
        return openApi;
    }

    private static SecurityScheme securityScheme(PlatformOpenApiProperties properties) {
        return switch (properties.getAuthenticationType()) {
            case NONE, CLIENT_ID_SECRET -> null;
            case JWT -> new SecurityScheme()
                .scheme(JWT_SCHEME)
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat(BEARER_FORMAT);
            case BASIC_AUTH -> new SecurityScheme()
                .scheme(BASIC_AUTH_SCHEME)
                .type(SecurityScheme.Type.HTTP);
            case API_KEY -> new SecurityScheme()
                .scheme(APIKEY_SCHEMA)
                .type(SecurityScheme.Type.APIKEY)
                .in(properties.getApiKeyLocation())
                .name("X-API-KEY");
        };
    }

    private void addSchemaClientId(OpenAPI openApi) {
        SecurityScheme clientId = new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .in(SecurityScheme.In.HEADER)
            .name("X-Client-Id");

        SecurityScheme clientSecret = new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .in(SecurityScheme.In.HEADER)
            .name("X-Client-Secret");

        openApi.components(new Components()
            .addSecuritySchemes("ClientId", clientId)
            .addSecuritySchemes("ClientSecret", clientSecret));

        openApi.addSecurityItem(new SecurityRequirement()
            .addList("ClientId")
            .addList("ClientSecret"));
    }
}
