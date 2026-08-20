package com.company.platform.schedule.demo.application.impl;

import com.company.platform.schedule.demo.application.NotificationUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService implements NotificationUseCase {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Override
    public void send(String notificationId) {
        log.info("Sending notification notificationId={}", notificationId);
    }
}
