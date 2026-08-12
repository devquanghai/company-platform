package com.company.platform.queue.publish.internal.adapter.rabbit;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

record RabbitPublisherResources(
    RabbitTemplate template
) {
}
