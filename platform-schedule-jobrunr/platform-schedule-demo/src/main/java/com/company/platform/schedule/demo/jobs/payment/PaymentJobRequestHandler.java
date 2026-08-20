package com.company.platform.schedule.demo.jobs.payment;

import com.company.platform.schedule.demo.application.PaymentUseCase;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.stereotype.Component;

@Component
public class PaymentJobRequestHandler implements JobRequestHandler<PaymentJobRequest> {

    private final PaymentUseCase paymentUseCase;

    public PaymentJobRequestHandler(PaymentUseCase paymentUseCase) {
        this.paymentUseCase = paymentUseCase;
    }

    @Override
    @Job(name = "Process payment", retries = 5)
    public void run(PaymentJobRequest request) {
        paymentUseCase.process(request.paymentId());
    }
}
