package com.company.platform.schedule;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class JobRunrDependencyContractTest {

    @Test
    void exposesNativeJobRunrApisTransitively() {
        assertThatCode(() -> Class.forName("org.jobrunr.scheduling.JobScheduler"))
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName("org.jobrunr.scheduling.JobRequestScheduler"))
                .doesNotThrowAnyException();
        assertThatCode(() -> Class.forName("org.jobrunr.jobs.annotations.Recurring"))
                .doesNotThrowAnyException();
    }
}
