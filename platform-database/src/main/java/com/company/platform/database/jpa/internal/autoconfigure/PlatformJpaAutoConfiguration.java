package com.company.platform.database.jpa.internal.autoconfigure;

import java.util.Map;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.cfg.QuerySettings;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Applies conservative Hibernate defaults through Hibernate's supported settings.
 */
@AutoConfiguration(before = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass({EntityManagerFactory.class, HibernatePropertiesCustomizer.class})
public class PlatformJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HibernatePropertiesCustomizer.class)
    HibernatePropertiesCustomizer platformDatabaseHibernatePropertiesCustomizer() {
        return PlatformJpaAutoConfiguration::applySafeDefaults;
    }

    private static void applySafeDefaults(Map<String, Object> properties) {
        properties.putIfAbsent(JdbcSettings.JDBC_TIME_ZONE, "UTC");
        properties.putIfAbsent(QuerySettings.FAIL_ON_PAGINATION_OVER_COLLECTION_FETCH, true);
    }
}
