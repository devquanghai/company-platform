package com.company.platform.schedule.demo.jobs.settlement;

import com.company.platform.schedule.demo.application.SettlementUseCase;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.annotations.Recurring;
import org.springframework.stereotype.Component;

@Component
public class DailySettlementRecurringJob {

    private final SettlementUseCase settlementUseCase;

    public DailySettlementRecurringJob(SettlementUseCase settlementUseCase) {
        this.settlementUseCase = settlementUseCase;
    }

    @Recurring(id = "daily-settlement", cron = "0 1 * * *")
    @Job(name = "Daily settlement", retries = 5)
    public void run() {
        LocalDate businessDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        settlementUseCase.execute(businessDate);
        throw new RuntimeException("Simulated failure for testing retries and idempotency");
    }
}
