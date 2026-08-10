package com.company.platform.queue.topology.internal.application;

import com.company.platform.queue.topology.internal.port.out.QueueTopologyManager;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.domain.policy.TopologyDeclarationMode;
import org.springframework.context.SmartLifecycle;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class QueueTopologyLifecycle implements SmartLifecycle {
    private final PlatformQueueProperties properties;
    private final List<QueueTopologyManager> managers;
    private final AtomicBoolean running = new AtomicBoolean();

    public QueueTopologyLifecycle(
        PlatformQueueProperties properties, List<QueueTopologyManager> managers
    ) {
        this.properties = properties;
        this.managers = List.copyOf(managers);
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        TopologyDeclarationMode mode =
            properties.getTopology().getMode();
        if (mode == TopologyDeclarationMode.DISABLED) {
            return;
        }
        if (mode == TopologyDeclarationMode.DECLARE_IF_MISSING
            || mode == TopologyDeclarationMode.DECLARE_AND_VALIDATE) {
            managers.forEach(manager -> {
                var result = manager.provision();
                if (!result.errorCodes().isEmpty()) {
                    throw new IllegalStateException(
                        "queue topology provisioning failed: " + result.errorCodes());
                }
            });
        }
        if (mode == TopologyDeclarationMode.VALIDATE_ONLY
            || mode == TopologyDeclarationMode.DECLARE_AND_VALIDATE) {
            managers.forEach(manager -> {
                var result = manager.validate();
                if (!result.valid()) {
                    throw new IllegalStateException(
                        "queue topology validation failed: " + result.errorCodes());
                }
            });
        }
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
