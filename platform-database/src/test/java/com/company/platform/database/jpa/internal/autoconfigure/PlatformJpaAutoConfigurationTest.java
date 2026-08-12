package com.company.platform.database.jpa.internal.autoconfigure;

import java.util.HashMap;
import java.util.Map;

import org.hibernate.cfg.JdbcSettings;
import org.hibernate.cfg.QuerySettings;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformJpaAutoConfigurationTest {

    @Test
    void nativeDefaultsHaveLowerPrecedenceThanApplicationConfiguration() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource(
                "applicationConfiguration",
                Map.of("spring.jpa.hibernate.ddl-auto", "none")));

        new PlatformDatabaseDefaultsEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("none");
    }

    @Test
    void hibernateCustomizerUsesNativeSettingsAndPreservesOverrides() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(PlatformJpaAutoConfiguration.class))
                .run(context -> {
                    HibernatePropertiesCustomizer customizer = context.getBean(HibernatePropertiesCustomizer.class);
                    Map<String, Object> properties = new HashMap<>();
                    properties.put(JdbcSettings.JDBC_TIME_ZONE, "Asia/Ho_Chi_Minh");

                    customizer.customize(properties);

                    assertThat(properties)
                            .containsEntry(JdbcSettings.JDBC_TIME_ZONE, "Asia/Ho_Chi_Minh")
                            .containsEntry(QuerySettings.FAIL_ON_PAGINATION_OVER_COLLECTION_FETCH, true);
                });
    }
}
