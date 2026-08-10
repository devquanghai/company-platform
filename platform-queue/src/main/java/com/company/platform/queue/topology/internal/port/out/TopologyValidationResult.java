package com.company.platform.queue.topology.internal.port.out;

import java.util.List;

public record TopologyValidationResult(boolean valid, List<String> errorCodes) {
    public TopologyValidationResult {
        errorCodes = List.copyOf(errorCodes);
    }
}
