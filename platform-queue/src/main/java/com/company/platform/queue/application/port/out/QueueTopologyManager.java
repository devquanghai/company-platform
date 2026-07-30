package com.company.platform.queue.application.port.out;

public interface QueueTopologyManager {
    TopologyValidationResult validate();
    TopologyProvisionResult provision();
}
