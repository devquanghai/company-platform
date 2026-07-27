package com.company.platform.logging.architecture;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformLoggingArtifactTest {

    @Test
    void registersEveryAutoConfigurationWithoutTopLevelLogbackOverride() throws Exception {
        String imports = resource(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        List<String> required = List.of(
            "PlatformLoggingAutoConfiguration",
            "MaskingAutoConfiguration",
            "CryptoAutoConfiguration",
            "LoggingAspectAutoConfiguration",
            "StructuredLoggingAutoConfiguration",
            "LoggingAuditAutoConfiguration",
            "LoggingMetricsAutoConfiguration",
            "LogbackAutoConfiguration");
        required.forEach(name -> assertThat(imports).contains(name));
        assertThat(getClass().getClassLoader().getResource("logback-spring.xml")).isNull();
        assertThat(getClass().getClassLoader().getResource("logback.xml")).isNull();
    }

    @Test
    void publishesVietnameseMetadataWithDefaultsAndSecurityDescriptions() throws Exception {
        String metadata = resource(
            "META-INF/additional-spring-configuration-metadata.json");
        assertThat(metadata)
            .contains("\"platform.logging.enabled\"")
            .contains("\"platform.logging.masking.mandatory-fields\"")
            .contains("\"platform.logging.crypto.max-envelope-length\"")
            .contains("\"defaultValue\"")
            .contains("dữ liệu nhạy cảm")
            .contains("không thể xóa");
    }

    @Test
    void packagesReusableLogbackFragments() {
        List<String> fragments = List.of(
            "defaults.xml", "console-text-appender.xml", "console-json-appender.xml",
            "rolling-file-appender.xml", "async-appender.xml", "audit-appender.xml",
            "platform-loggers.xml");
        fragments.forEach(name -> assertThat(getClass().getClassLoader().getResource(
            "com/company/platform/logging/logback/" + name)).isNotNull());
    }

    private static String resource(String name) throws Exception {
        try (InputStream stream =
                 PlatformLoggingArtifactTest.class.getClassLoader()
                     .getResourceAsStream(name)) {
            assertThat(stream).as(name).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
