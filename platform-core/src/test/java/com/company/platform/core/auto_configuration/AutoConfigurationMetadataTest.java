package com.company.platform.core.auto_configuration;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AutoConfigurationMetadataTest {

    @Test
    void listsEveryPlatformAutoConfiguration() throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(stream).isNotNull();
            List<String> imports = new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                .lines()
                .filter(line -> !line.isBlank())
                .toList();
            assertThat(imports).containsExactly(
                PlatformCoreAutoConfiguration.class.getName(),
                PlatformJacksonAutoConfiguration.class.getName(),
                PlatformOpenApiAutoConfiguration.class.getName(),
                PlatformTaskExecutionAutoConfiguration.class.getName(),
                PlatformWebMvcAutoConfiguration.class.getName(),
                PlatformAuditAutoConfiguration.class.getName()
            );
        }

        assertResourceContains(
            "META-INF/spring-autoconfigure-metadata.properties",
            PlatformJacksonAutoConfiguration.class.getName(),
            PlatformOpenApiAutoConfiguration.class.getName()
        );
        assertResourceContains(
            "META-INF/spring-configuration-metadata.json",
            "platform.core.jackson.strict-scalar-coercion",
            "platform.core.exception-handling.include-rejected-value",
            "platform.core.openapi.authentication-type",
            "platform.core.task-execution.context-propagation-enabled",
            "platform.core.web.trace-filter-enabled",
            "platform.core.web.request-logging-enabled",
            "platform.core.web.include-payload",
            "platform.core.web.max-payload-length",
            "platform.core.web.server-timing-enabled",
            "platform.core.web.request-caching-enabled",
            "platform.core.web.max-cached-request-body-size",
            "platform.core.audit.enabled",
            "platform.core.audit.default-auditor",
            "platform.core.audit.publish-failure-events",
            "Bật cấu hình Jackson an toàn và nhất quán của platform.",
            "Không khai báo cơ chế xác thực mặc định trong tài liệu OpenAPI."
        );

        assertCompleteVietnameseConfigurationMetadata();
    }

    private void assertCompleteVietnameseConfigurationMetadata() throws IOException {
        JsonNode additional = readJsonResource(
            "META-INF/additional-spring-configuration-metadata.json"
        );
        assertThat(additional.get("groups").size()).isEqualTo(7);
        assertThat(additional.get("properties").size()).isEqualTo(48);

        for (JsonNode property : additional.get("properties")) {
            assertThat(property.get("name").asText()).startsWith("platform.core.");
            assertThat(property.get("description").asText()).isNotBlank();
            assertThat(property.has("defaultValue")).isTrue();
        }

        JsonNode generated = readJsonResource("META-INF/spring-configuration-metadata.json");
        int platformPropertyCount = 0;
        for (JsonNode property : generated.get("properties")) {
            if (property.get("name").asText().startsWith("platform.core.")) {
                platformPropertyCount++;
                assertThat(property.get("description").asText()).isNotBlank();
                assertThat(property.has("defaultValue")).isTrue();
            }
        }
        assertThat(platformPropertyCount).isEqualTo(48);
    }

    private JsonNode readJsonResource(String resource) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            return JsonMapper.builder().build().readTree(stream);
        }
    }

    private void assertResourceContains(String resource, String... values) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).isNotNull();
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).contains(values);
        }
    }
}
