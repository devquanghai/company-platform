package com.company.platform.queue.topology.internal.port.out;

public interface QueueTopologyManager {
    TopologyValidationResult validate();
    TopologyProvisionResult provision();
}
