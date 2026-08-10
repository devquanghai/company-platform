package com.company.platform.queue.autoconfigure.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.unit.DataSize;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
public class MessageProperties {
    private DataSize maxPayloadSize = DataSize.ofMegabytes(1);
    private DataSize maxEnvelopeSize = DataSize.ofMegabytes(2);
    private Set<String> allowedHeaders = new LinkedHashSet<>();
}
