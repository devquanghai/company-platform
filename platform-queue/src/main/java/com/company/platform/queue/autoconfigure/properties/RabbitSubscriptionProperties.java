package com.company.platform.queue.autoconfigure.properties;

import com.company.platform.queue.api.rabbit.RabbitQueueType;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class RabbitSubscriptionProperties {
    private String queue;
    private RabbitQueueType queueType = RabbitQueueType.QUORUM;
    private boolean durable = true;
    private boolean exclusive;
    private boolean autoDelete;
    private int prefetch = 20;
    private int concurrency = 1;
    private int maxConcurrency = 1;
    private String deadLetterExchange;
    private String deadLetterRoutingKey;
    private Duration messageTtl;
    private Duration queueTtl;
    private Long maxLength;
    private Integer deliveryLimit;
    private boolean singleActiveConsumer;
    private Map<String, Object> queueArguments = new LinkedHashMap<>();
}
