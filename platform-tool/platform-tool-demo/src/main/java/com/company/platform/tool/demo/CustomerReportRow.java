package com.company.platform.tool.demo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerReportRow(long id, String customerCode, String customerName, BigDecimal amount, BigDecimal interestRate, LocalDateTime createdAt, String status) { }
