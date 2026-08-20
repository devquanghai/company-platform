package com.company.platform.schedule.demo.jobs.retry;

import org.jobrunr.jobs.lambdas.JobRequest;

public record RetryDemoJobRequest(String requestId, boolean fail) implements JobRequest {

    @Override
    public Class<RetryDemoJobRequestHandler> getJobRequestHandler() {
        return RetryDemoJobRequestHandler.class;
    }
}
