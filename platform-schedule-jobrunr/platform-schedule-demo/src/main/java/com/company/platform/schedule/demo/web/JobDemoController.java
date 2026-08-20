package com.company.platform.schedule.demo.web;

import com.company.platform.schedule.demo.jobs.notification.NotificationJobRequest;
import com.company.platform.schedule.demo.jobs.payment.PaymentJobRequest;
import com.company.platform.schedule.demo.jobs.retry.RetryDemoJobRequest;
import com.company.platform.schedule.demo.jobs.settlement.SettlementJobRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.jobrunr.jobs.JobId;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/jobs")
public class JobDemoController {

    private final JobRequestScheduler jobRequestScheduler;

    public JobDemoController(JobRequestScheduler jobRequestScheduler) {
        this.jobRequestScheduler = jobRequestScheduler;
    }

    @PostMapping("/notifications/{notificationId}")
    public Map<String, String> enqueueNotification(@PathVariable String notificationId) {
        JobId jobId = jobRequestScheduler.enqueue(new NotificationJobRequest(notificationId));
        return Map.of("jobId", jobId.toString());
    }

    @PostMapping("/payments/{paymentId}")
    public Map<String, String> schedulePayment(
            @PathVariable String paymentId,
            @RequestParam(defaultValue = "30") long delaySeconds) {
        JobId jobId = jobRequestScheduler.schedule(
                Instant.now().plusSeconds(delaySeconds),
                new PaymentJobRequest(paymentId));
        return Map.of("jobId", jobId.toString());
    }

    @PostMapping("/settlements")
    public Map<String, String> enqueueSettlement(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate businessDate) {
        JobId jobId = jobRequestScheduler.enqueue(new SettlementJobRequest(businessDate));
        return Map.of("jobId", jobId.toString());
    }

    @PostMapping("/retry-demo/{requestId}")
    public Map<String, String> retryDemo(
            @PathVariable String requestId,
            @RequestParam(defaultValue = "true") boolean fail) {
        JobId jobId = jobRequestScheduler.enqueue(new RetryDemoJobRequest(requestId, fail));
        return Map.of("jobId", jobId.toString());
    }
}
