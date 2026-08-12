package com.company.platform.database.datasource.internal.autoconfigure;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PlatformDataSourceAutoConfiguration.class));

    @Test
    void buildsHikariFromNativeSpringAndHikariProperties() {
        contextRunner
                .withPropertyValues(
                        "spring.datasource.url=jdbc:postgresql://localhost/example",
                        "spring.datasource.username=database-user",
                        "spring.datasource.password=test-only",
                        "spring.datasource.hikari.pool-name=native-properties-pool",
                        "spring.datasource.hikari.maximum-pool-size=13")
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    HikariDataSource dataSource = context.getBean(HikariDataSource.class);
                    assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost/example");
                    assertThat(dataSource.getUsername()).isEqualTo("database-user");
                    assertThat(dataSource.getPoolName()).isEqualTo("native-properties-pool");
                    assertThat(dataSource.getMaximumPoolSize()).isEqualTo(13);
                });
    }

    @Test
    void backsOffForApplicationDataSource() {
        contextRunner
                .withUserConfiguration(ApplicationDataSourceConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context.getBean(DataSource.class))
                            .isSameAs(context.getBean("applicationDataSource"));
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationDataSourceConfiguration {

        @Bean
        HikariDataSource applicationDataSource() {
            return new HikariDataSource();
        }
    }
}
