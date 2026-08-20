package com.company.platform.schedule.demo.application.impl;

import com.company.platform.schedule.demo.application.PaymentUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentService implements PaymentUseCase {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Override
    public void process(String paymentId) {
        log.info("Processing payment paymentId={}", paymentId);
    }
}
