package com.company.platform.integration.queue.internal.application;

import com.company.platform.integration.queue.internal.domain.QueueDemoEvent;
import com.company.platform.queue.api.publish.PublishResult;

public record QueuePublishOutcome(QueueDemoEvent event, PublishResult result) { }
