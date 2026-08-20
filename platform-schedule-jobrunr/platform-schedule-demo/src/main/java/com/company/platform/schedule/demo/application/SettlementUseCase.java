package com.company.platform.schedule.demo.application;

import java.time.LocalDate;

public interface SettlementUseCase {
    void execute(LocalDate businessDate);
}
