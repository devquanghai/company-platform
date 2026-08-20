package com.company.platform.schedule.demo.multiinstance;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;

public class ProbeJobRequestHandler implements JobRequestHandler<ProbeJobRequest> {

    @Override
    @Job(name = "Multi-instance execution probe", retries = 0)
    public void run(ProbeJobRequest request) {
        ProbeExecutionTracker.recordExecution();
    }
}
