package com.company.platform.schedule.demo.jobs.notification;

import org.jobrunr.jobs.lambdas.JobRequest;

public record NotificationJobRequest(String notificationId) implements JobRequest {

    @Override
    public Class<NotificationJobRequestHandler> getJobRequestHandler() {
        return NotificationJobRequestHandler.class;
    }
}
