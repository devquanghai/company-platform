package com.company.platform.database;

import javax.sql.DataSource;

import java.sql.Connection;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.JdbcSettings;
import org.hibernate.cfg.QuerySettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernateProperties;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = PlatformDatabaseTestApplication.class,
        properties = {
                "spring.datasource.hikari.pool-name=platform-database-test",
                "spring.datasource.hikari.maximum-pool-size=7",
                "spring.datasource.hikari.minimum-idle=1",
                "spring.datasource.hikari.auto-commit=false",
        })
@Testcontainers(disabledWithoutDocker = true)
class PlatformDatabaseIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17.6-alpine"))
            .withDatabaseName("platform_database")
            .withUsername("platform_database")
            .withPassword("test-only")
            .withInitScript("db/platform-database-schema.sql");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private DatabaseProbeRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JpaProperties jpaProperties;

    @Autowired
    private HibernateProperties hibernateProperties;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void usesHikariAndBindsNativeConfiguration() throws Exception {
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);

        assertThat(hikari.getPoolName()).isEqualTo("platform-database-test");
        assertThat(hikari.getMaximumPoolSize()).isEqualTo(7);
        assertThat(hikari.getMinimumIdle()).isOne();
        assertThat(hikari.isAutoCommit()).isFalse();
        try (Connection connection = hikari.getConnection()) {
            assertThat(connection.isValid(5)).isTrue();
        }
    }

    @Test
    void appliesSafeJpaAndHibernateDefaults() {
        assertThat(jpaProperties.getOpenInView()).isFalse();
        assertThat(jpaProperties.isShowSql()).isFalse();
        assertThat(hibernateProperties.getDdlAuto()).isEqualTo("validate");
        assertThat(entityManagerFactory.getProperties())
                .containsEntry(JdbcSettings.JDBC_TIME_ZONE, "UTC")
                .containsEntry(QuerySettings.FAIL_ON_PAGINATION_OVER_COLLECTION_FETCH, true);
    }

    @Test
    void commitsJpaRepositoryWork() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        Long id = transaction.execute(status -> repository.saveAndFlush(new DatabaseProbeEntity("committed")).getId());

        assertThat(id).isNotNull();
        assertThat(repository.findByValue("committed")).isPresent();
    }

    @Test
    void rollsBackJpaRepositoryWork() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            repository.saveAndFlush(new DatabaseProbeEntity("rolled-back"));
            throw new RollbackProbeException();
        })).isInstanceOf(RollbackProbeException.class);

        assertThat(repository.findByValue("rolled-back")).isEmpty();
    }

    private static final class RollbackProbeException extends RuntimeException {
    }
}
