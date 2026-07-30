package com.company.platform.queue.reliability.outbox;

import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OutboxPollingLifecycle implements SmartLifecycle {
    private final OutboxPollingPublisher publisher;
    private final Duration interval;
    private final AtomicBoolean running = new AtomicBoolean();
    private ScheduledExecutorService executor;

    public OutboxPollingLifecycle(
        OutboxPollingPublisher publisher, Duration interval
    ) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.interval = Objects.requireNonNull(interval, "interval");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("outbox poll interval must be positive");
        }
    }

    @Override
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("platform-queue-outbox-", 0).daemon(true).factory());
        executor.scheduleWithFixedDelay(
            this::pollSafely, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(
                Math.min(interval.toMillis(), 5_000), TimeUnit.MILLISECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    private void pollSafely() {
        try {
            publisher.runOnce();
        } catch (RuntimeException ignored) {
            // Store/broker health and metrics expose failure without killing the poller.
        }
    }
}
