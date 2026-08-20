package com.company.platform.schedule.demo.multiinstance;

import org.jobrunr.jobs.lambdas.JobRequest;

public record ProbeJobRequest(String businessKey) implements JobRequest {

    @Override
    public Class<ProbeJobRequestHandler> getJobRequestHandler() {
        return ProbeJobRequestHandler.class;
    }
}
