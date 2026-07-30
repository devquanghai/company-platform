package com.company.platform.queue.serialization.migration;

import tools.jackson.databind.JsonNode;

public interface MessageUpcaster {
    boolean supports(String eventType, int fromVersion, int toVersion);
    JsonNode upcast(JsonNode payload);
}
