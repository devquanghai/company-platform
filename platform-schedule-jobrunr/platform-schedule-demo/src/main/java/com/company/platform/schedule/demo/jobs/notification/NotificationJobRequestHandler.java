package com.company.platform.schedule.demo.jobs.notification;

import com.company.platform.schedule.demo.application.NotificationUseCase;
import org.jobrunr.jobs.annotations.Job;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.springframework.stereotype.Component;

@Component
public class NotificationJobRequestHandler implements JobRequestHandler<NotificationJobRequest> {

    private final NotificationUseCase notificationUseCase;

    public NotificationJobRequestHandler(NotificationUseCase notificationUseCase) {
        this.notificationUseCase = notificationUseCase;
    }

    @Override
    @Job(name = "Send notification", retries = 5)
    public void run(NotificationJobRequest request) {
        notificationUseCase.send(request.notificationId());
    }
}
