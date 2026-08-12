package com.company.platform.integration.queue.internal.adapter.in.web;

import com.company.platform.core.rest.response.ApiResponse;
import com.company.platform.integration.queue.internal.application.QueueMessageProbe;
import com.company.platform.integration.queue.internal.application.QueuePublishService;
import com.company.platform.integration.queue.internal.domain.QueueMode;
import com.company.platform.integration.queue.internal.domain.ConsumedQueueMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.context.annotation.Profile;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/queue/realtime")
public class RealtimeQueueController {
    private final QueuePublishService publisher;
    private final QueueMessageProbe probe;

    public RealtimeQueueController(
        QueuePublishService publisher, QueueMessageProbe probe
    ) {
        this.publisher = publisher;
        this.probe = probe;
    }

    @PostMapping
    public ApiResponse<QueuePublishResponse> publish(
        @Valid @RequestBody QueueMessageRequest request
    ) {
        var outcome = publisher.publish(QueueMode.REALTIME, request.businessKey(),
            request.message(), request.attributes());
        return ApiResponse.success(QueuePublishResponse.from(
            "REALTIME", outcome.event().eventId(), outcome.result()));
    }

    @GetMapping
    public ApiResponse<List<ConsumedQueueMessage>> consumed(
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit
    ) { return ApiResponse.success(probe.latest(QueueMode.REALTIME, limit)); }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.success(Map.of("mode", "REALTIME", "totalConsumed",
            probe.total(QueueMode.REALTIME), "processing", "immediate"));
    }

    @DeleteMapping
    public ApiResponse<Void> clear() {
        probe.clear(QueueMode.REALTIME);
        return ApiResponse.success(null);
    }
}
