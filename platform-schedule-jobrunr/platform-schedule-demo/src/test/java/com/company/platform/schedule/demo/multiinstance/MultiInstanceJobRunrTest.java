package com.company.platform.schedule.demo.multiinstance;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class MultiInstanceJobRunrTest {

    @TempDir
    Path tempDir;

    @Test
    void oneJobInstanceIsExecutedOnceAcrossTwoBackgroundJobServers() throws Exception {
        ProbeExecutionTracker.reset();

        String databasePath = tempDir.resolve("jobrunr-cluster").toAbsolutePath().toString();
        String jdbcUrl = "jdbc:h2:file:" + databasePath + ";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE";

        try (ConfigurableApplicationContext serverOne = startContext(jdbcUrl, false);
             ConfigurableApplicationContext serverTwo = startContext(jdbcUrl, true)) {

            JobRequestScheduler scheduler = serverOne.getBean(JobRequestScheduler.class);
            scheduler.enqueue(new ProbeJobRequest("BUSINESS-001"));

            assertThat(ProbeExecutionTracker.latch().await(20, TimeUnit.SECONDS)).isTrue();

            // Give the second server enough time to poll the same shared storage as a duplicate-safety check.
            TimeUnit.SECONDS.sleep(2);
            assertThat(ProbeExecutionTracker.executions()).isEqualTo(1);
        }
    }

    private ConfigurableApplicationContext startContext(String jdbcUrl, boolean skipCreate) {
        return new SpringApplicationBuilder(MultiInstanceTestApplication.class)
                .web(WebApplicationType.NONE)
                .properties(Map.ofEntries(
                        Map.entry("spring.datasource.url", jdbcUrl),
                        Map.entry("spring.datasource.username", "sa"),
                        Map.entry("spring.datasource.password", ""),
                        Map.entry("jobrunr.database.type", "sql"),
                        Map.entry("jobrunr.database.skip-create", Boolean.toString(skipCreate)),
                        Map.entry("jobrunr.job-scheduler.enabled", "true"),
                        Map.entry("jobrunr.background-job-server.enabled", "true"),
                        Map.entry("jobrunr.background-job-server.worker-count", "1"),
                        Map.entry("jobrunr.background-job-server.poll-interval-in-seconds", "1"),
                        Map.entry("jobrunr.dashboard.enabled", "false"),
                        Map.entry("jobrunr.miscellaneous.allow-anonymous-data-usage", "false")
                ))
                .run();
    }
}
