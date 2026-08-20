CREATE TABLE IF NOT EXISTS settlement_execution (
    business_date DATE PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NULL
);
