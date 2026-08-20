package com.company.platform.schedule.demo.application.impl;

import com.company.platform.schedule.demo.application.SettlementUseCase;
import com.company.platform.schedule.demo.persistence.SettlementExecutionRepository;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class IdempotentSettlementService implements SettlementUseCase {

    private static final Logger log = LoggerFactory.getLogger(IdempotentSettlementService.class);

    private final SettlementTransactionalProcessor processor;
    private final SettlementExecutionRepository executionRepository;

    public IdempotentSettlementService(
            SettlementTransactionalProcessor processor,
            SettlementExecutionRepository executionRepository) {
        this.processor = processor;
        this.executionRepository = executionRepository;
    }

    @Override
    public void execute(LocalDate businessDate) {
        try {
            processor.process(businessDate);
        } catch (DuplicateKeyException duplicate) {
            if (executionRepository.isCompleted(businessDate)) {
                log.info("Settlement already completed; skipping duplicate businessDate={}", businessDate);
                return;
            }
            throw duplicate;
        }
    }
}
