package com.company.platform.schedule.demo.jobs.settlement;

import java.time.LocalDate;
import org.jobrunr.jobs.lambdas.JobRequest;

public record SettlementJobRequest(LocalDate businessDate) implements JobRequest {

    @Override
    public Class<SettlementJobRequestHandler> getJobRequestHandler() {
        return SettlementJobRequestHandler.class;
    }
}
