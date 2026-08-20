package com.company.platform.schedule.demo.jobs.settlement;

import com.company.platform.schedule.demo.application.SettlementUseCase;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.stereotype.Component;

@Component
public class SettlementJobRequestHandler implements JobRequestHandler<SettlementJobRequest> {

    private final SettlementUseCase settlementUseCase;

    public SettlementJobRequestHandler(SettlementUseCase settlementUseCase) {
        this.settlementUseCase = settlementUseCase;
    }

    @Override
    @Job(name = "Manual settlement", retries = 5)
    public void run(SettlementJobRequest request) {
        settlementUseCase.execute(request.businessDate());
    }
}
