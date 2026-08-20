package com.company.platform.schedule.demo.jobs.settlement;

import com.company.platform.schedule.demo.application.SettlementUseCase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SettlementJobRequestHandlerTest {

    @Test
    void delegatesToBusinessUseCase() {
        SettlementUseCase useCase = mock(SettlementUseCase.class);
        SettlementJobRequestHandler handler = new SettlementJobRequestHandler(useCase);
        LocalDate businessDate = LocalDate.of(2026, 8, 17);

        handler.run(new SettlementJobRequest(businessDate));

        verify(useCase).execute(businessDate);
    }
}
