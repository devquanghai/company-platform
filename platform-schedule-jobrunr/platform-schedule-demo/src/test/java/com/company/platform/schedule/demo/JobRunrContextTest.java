package com.company.platform.schedule.demo;

import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JobRunrContextTest {

    @Autowired
    private JobScheduler jobScheduler;

    @Autowired
    private JobRequestScheduler jobRequestScheduler;

    @Test
    void createsNativeJobRunrSchedulers() {
        assertThat(jobScheduler).isNotNull();
        assertThat(jobRequestScheduler).isNotNull();
    }
}
