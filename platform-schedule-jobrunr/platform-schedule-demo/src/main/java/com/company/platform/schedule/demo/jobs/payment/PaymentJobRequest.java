package com.company.platform.schedule.demo.jobs.payment;

import org.jobrunr.jobs.lambdas.JobRequest;

public record PaymentJobRequest(String paymentId) implements JobRequest {

    @Override
    public Class<PaymentJobRequestHandler> getJobRequestHandler() {
        return PaymentJobRequestHandler.class;
    }
}
