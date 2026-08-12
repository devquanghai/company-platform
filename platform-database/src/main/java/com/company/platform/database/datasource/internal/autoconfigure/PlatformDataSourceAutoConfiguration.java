package com.company.platform.database.datasource.internal.autoconfigure;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Builds the conventional single {@link DataSource} from Spring Boot and Hikari native
 * configuration properties.
 */
@AutoConfiguration(before = DataSourceAutoConfiguration.class)
@ConditionalOnClass({DataSource.class, HikariDataSource.class})
@ConditionalOnMissingBean(DataSource.class)
@EnableConfigurationProperties(DataSourceProperties.class)
public class PlatformDataSourceAutoConfiguration {

    static final String HIKARI_DATA_SOURCE = "com.zaxxer.hikari.HikariDataSource";

    @Bean
    @ConditionalOnProperty(
            prefix = "spring.datasource",
            name = "type",
            havingValue = HIKARI_DATA_SOURCE,
            matchIfMissing = true)
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(
            DataSourceProperties properties,
            JdbcConnectionDetails connectionDetails,
            Environment environment) {
        DataSourceBuilder<?> builder = DataSourceBuilder.create(properties.getClassLoader())
                .type(HikariDataSource.class)
                .url(connectionDetails.getJdbcUrl())
                .username(connectionDetails.getUsername())
                .password(connectionDetails.getPassword());
        if (!environment.containsProperty("spring.datasource.hikari.data-source-class-name")) {
            builder.driverClassName(connectionDetails.getDriverClassName());
        }
        HikariDataSource dataSource = (HikariDataSource) builder.build();
        if (StringUtils.hasText(properties.getName())) {
            dataSource.setPoolName(properties.getName());
        }
        return dataSource;
    }
}
