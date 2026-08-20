package com.company.platform.schedule.demo.jobs.retry;

import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RetryDemoJobRequestHandler implements JobRequestHandler<RetryDemoJobRequest> {

    private static final Logger log = LoggerFactory.getLogger(RetryDemoJobRequestHandler.class);

    @Override
    @Job(name = "Retry demo", retries = 5)
    public void run(RetryDemoJobRequest request) {
        log.info("Executing retry demo requestId={} fail={}", request.requestId(), request.fail());
        if (request.fail()) {
            throw new IllegalStateException("Intentional failure for JobRunr retry demonstration");
        }
    }
}
