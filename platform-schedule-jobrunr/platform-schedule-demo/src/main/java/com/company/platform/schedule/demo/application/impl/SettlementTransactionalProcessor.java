package com.company.platform.schedule.demo.application.impl;

import com.company.platform.schedule.demo.persistence.SettlementExecutionRepository;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementTransactionalProcessor {

    private static final Logger log = LoggerFactory.getLogger(SettlementTransactionalProcessor.class);

    private final SettlementExecutionRepository executionRepository;

    public SettlementTransactionalProcessor(SettlementExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    @Transactional
    public void process(LocalDate businessDate) {
        executionRepository.insertStarted(businessDate);

        // Demo only: replace this section with the real settlement business transaction.
        log.info("Executing settlement businessDate={}", businessDate);

        executionRepository.markCompleted(businessDate);
    }
}
