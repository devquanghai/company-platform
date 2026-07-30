package com.company.platform.queue.autoconfigure.properties;

import com.company.platform.queue.domain.model.PublishMode;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

@Getter
@Setter
public class DestinationProperties {
    private boolean enabled = true;
    private String broker;
    private boolean producerEnabled = true;
    private boolean consumerEnabled = true;
    private PublishMode publishMode = PublishMode.DIRECT;
    private Duration sendTimeout = Duration.ofSeconds(10);
    private KafkaDestinationProperties kafka = new KafkaDestinationProperties();
    private RabbitDestinationProperties rabbit = new RabbitDestinationProperties();
    private SerializationProperties serialization = new SerializationProperties();
}
