package com.company.platform.schedule.demo.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettlementExecutionRepository {

    private final JdbcTemplate jdbcTemplate;

    public SettlementExecutionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertStarted(LocalDate businessDate) {
        jdbcTemplate.update("""
                INSERT INTO settlement_execution (
                    business_date,
                    status,
                    started_at,
                    completed_at
                ) VALUES (?, 'PROCESSING', ?, NULL)
                """, businessDate, Timestamp.from(Instant.now()));
    }

    public boolean isCompleted(LocalDate businessDate) {
        Boolean completed = jdbcTemplate.queryForObject("""
                SELECT CASE WHEN status = 'COMPLETED' THEN TRUE ELSE FALSE END
                  FROM settlement_execution
                 WHERE business_date = ?
                """, Boolean.class, businessDate);
        return Boolean.TRUE.equals(completed);
    }

    public void markCompleted(LocalDate businessDate) {
        jdbcTemplate.update("""
                UPDATE settlement_execution
                   SET status = 'COMPLETED',
                       completed_at = ?
                 WHERE business_date = ?
                """, Timestamp.from(Instant.now()), businessDate);
    }
}
