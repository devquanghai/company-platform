package com.company.platform.queue.autoconfigure.properties;

import com.company.platform.queue.api.rabbit.RabbitExchangeType;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class RabbitDestinationProperties {
    private String exchange;
    private RabbitExchangeType exchangeType = RabbitExchangeType.DIRECT;
    private String routingKey = "";
    private boolean durable = true;
    private boolean autoDelete;
    private boolean internal;
    private boolean persistent = true;
    private boolean allowRoutingKeyOverride;
    private String alternateExchange;
    private Map<String, Object> exchangeArguments = new LinkedHashMap<>();
}
