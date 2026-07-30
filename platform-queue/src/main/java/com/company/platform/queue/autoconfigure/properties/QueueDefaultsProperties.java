package com.company.platform.queue.autoconfigure.properties;

import com.company.platform.queue.serialization.MessageSerializationFormat;
import com.company.platform.queue.domain.policy.TopologyDeclarationMode;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
public class QueueDefaultsProperties {
    private MessageSerializationFormat serialization = MessageSerializationFormat.JSON;
    private String contentType = "application/json";
    private boolean requireMessageId = true;
    private boolean requireCorrelationId = true;
    private boolean includeTraceContext = true;
    private boolean logPayload;
    private TopologyDeclarationMode topologyDeclarationMode =
        TopologyDeclarationMode.VALIDATE_ONLY;
    private int maxHeaders = 64;
    private int maxHeaderBytes = 8 * 1024;
    private int maxTotalHeaderBytes = 32 * 1024;
    private int maxPayloadBytes = 1024 * 1024;
    private int maxEnvelopeBytes = 2 * 1024 * 1024;
    private Set<String> allowedCustomHeaders = new LinkedHashSet<>();
}
