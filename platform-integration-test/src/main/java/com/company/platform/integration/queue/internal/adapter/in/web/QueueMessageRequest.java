package com.company.platform.integration.queue.internal.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record QueueMessageRequest(
    @NotBlank @Size(max = 128) String businessKey,
    @NotBlank @Size(max = 4096) String message,
    @Size(max = 20) Map<@Size(max = 64) String, @Size(max = 256) String> attributes
) { }
