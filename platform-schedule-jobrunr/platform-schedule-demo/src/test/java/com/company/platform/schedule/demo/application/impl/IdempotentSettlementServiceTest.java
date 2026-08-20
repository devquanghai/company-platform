package com.company.platform.schedule.demo.application.impl;

import com.company.platform.schedule.demo.persistence.SettlementExecutionRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdempotentSettlementServiceTest {

    @Test
    void treatsCompletedDuplicateAsSuccessfulReplay() {
        SettlementTransactionalProcessor processor = mock(SettlementTransactionalProcessor.class);
        SettlementExecutionRepository repository = mock(SettlementExecutionRepository.class);
        IdempotentSettlementService service = new IdempotentSettlementService(processor, repository);
        LocalDate date = LocalDate.of(2026, 8, 17);

        doThrow(new DuplicateKeyException("duplicate")).when(processor).process(date);
        when(repository.isCompleted(date)).thenReturn(true);

        service.execute(date);

        verify(repository).isCompleted(date);
    }

    @Test
    void rethrowsDuplicateIfPreviousExecutionIsNotCompleted() {
        SettlementTransactionalProcessor processor = mock(SettlementTransactionalProcessor.class);
        SettlementExecutionRepository repository = mock(SettlementExecutionRepository.class);
        IdempotentSettlementService service = new IdempotentSettlementService(processor, repository);
        LocalDate date = LocalDate.of(2026, 8, 17);

        doThrow(new DuplicateKeyException("duplicate")).when(processor).process(date);
        when(repository.isCompleted(date)).thenReturn(false);

        assertThatThrownBy(() -> service.execute(date))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
