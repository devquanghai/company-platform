package com.company.platform.integration.kafkaapp.api;

import com.company.platform.core.time.TimeProvider;
import com.company.platform.integration.kafkaapp.model.KafkaTestEvent;
import com.company.platform.integration.kafkaapp.service.KafkaMessageProbe;
import com.company.platform.queue.api.publish.MessagePublisher;
import com.company.platform.queue.api.publish.PublishRequest;
import com.company.platform.queue.api.publish.PublishResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/kafka")
public class KafkaTestController {
    private static final String DESTINATION = "kafka-test-events";

    private final MessagePublisher publisher;
    private final KafkaMessageProbe probe;
    private final TimeProvider time;

    public KafkaTestController(
        MessagePublisher publisher,
        KafkaMessageProbe probe,
        TimeProvider time
    ) {
        this.publisher = publisher;
        this.probe = probe;
        this.time = time;
    }

    @PostMapping("/messages")
    public ResponseEntity<KafkaPublishResponse> publish(
        @Valid @RequestBody KafkaPublishRequest request
    ) {
        String eventId = request.messageId() == null
            ? UUID.randomUUID().toString()
            : request.messageId();
        KafkaTestEvent event = new KafkaTestEvent(
            eventId, request.aggregateId(), request.message(), time.nowInstant());
        PublishResult result = publisher.publish(
            PublishRequest.builder(event)
                .destination(DESTINATION)
                .key(request.aggregateId())
                .messageId(eventId)
                .eventId(eventId)
                .correlationId(eventId)
                .eventType("KafkaTestEvent")
                .schemaVersion(1)
                .build());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(KafkaPublishResponse.from(result));
    }

    @GetMapping("/messages")
    public List<ReceivedKafkaMessage> received(
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit
    ) {
        return probe.latest(limit);
    }

    @GetMapping("/messages/{messageId}")
    public ResponseEntity<ReceivedKafkaMessage> receivedById(
        @PathVariable String messageId
    ) {
        return ResponseEntity.ofNullable(probe.find(messageId));
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "destination", DESTINATION,
            "totalReceived", probe.totalReceived());
    }

    @DeleteMapping("/messages")
    public ResponseEntity<Void> clear() {
        probe.clear();
        return ResponseEntity.noContent().build();
    }
}
