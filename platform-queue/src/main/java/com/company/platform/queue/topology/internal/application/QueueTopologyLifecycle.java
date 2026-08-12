package com.company.platform.queue.topology.internal.application;

import com.company.platform.queue.topology.internal.port.out.QueueTopologyManager;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.domain.policy.TopologyDeclarationMode;
import org.springframework.context.SmartLifecycle;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
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
        log.info("Queue topology lifecycle starting mode={} managers={}",
            mode, managers.size());
        if (mode == TopologyDeclarationMode.DISABLED) {
            return;
        }
        if (mode == TopologyDeclarationMode.DECLARE_IF_MISSING
            || mode == TopologyDeclarationMode.DECLARE_AND_VALIDATE) {
            managers.forEach(manager -> {
                var result = manager.provision();
                if (!result.errorCodes().isEmpty()) {
                    log.error("Queue topology provisioning failed errorCodes={}",
                        result.errorCodes());
                    throw new IllegalStateException(
                        "queue topology provisioning failed: " + result.errorCodes());
                }
                log.info("Queue topology provisioned created={} existing={}",
                    result.created(), result.existing());
            });
        }
        if (mode == TopologyDeclarationMode.VALIDATE_ONLY
            || mode == TopologyDeclarationMode.DECLARE_AND_VALIDATE) {
            managers.forEach(manager -> {
                var result = manager.validate();
                if (!result.valid()) {
                    log.error("Queue topology validation failed errorCodes={}",
                        result.errorCodes());
                    throw new IllegalStateException(
                        "queue topology validation failed: " + result.errorCodes());
                }
                log.info("Queue topology validation succeeded");
            });
        }
    }

    @Override
    public void stop() {
        running.set(false);
        log.info("Queue topology lifecycle stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
