package com.company.platform.queue.topology.internal.port.out;

import java.util.List;

public record TopologyProvisionResult(int created, int existing, List<String> errorCodes) {
    public TopologyProvisionResult {
        errorCodes = List.copyOf(errorCodes);
    }
}
