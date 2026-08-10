package com.company.platform.queue.autoconfigure.properties;

import com.company.platform.queue.domain.policy.TopologyDeclarationMode;
import com.company.platform.queue.serialization.MessageSerializationFormat;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/** @deprecated use {@link MessageProperties} and {@link TopologyProperties}. */
@Deprecated
@Getter
@Setter
public class QueueDefaultsProperties {
    private MessageSerializationFormat serialization = MessageSerializationFormat.JSON;
    private String contentType = "application/json";
    private boolean requireMessageId = true;
    private boolean requireCorrelationId = true;
    private boolean includeTraceContext = true;
    private boolean logPayload;
    private TopologyDeclarationMode topologyDeclarationMode;
    private Integer maxHeaders;
    private Integer maxHeaderBytes;
    private Integer maxTotalHeaderBytes;
    private Integer maxPayloadBytes;
    private Integer maxEnvelopeBytes;
    private Set<String> allowedCustomHeaders = new LinkedHashSet<>();
}
