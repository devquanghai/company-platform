package com.company.platform.schedule.demo.multiinstance;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

final class ProbeExecutionTracker {

    private static final AtomicInteger EXECUTIONS = new AtomicInteger();
    private static volatile CountDownLatch latch = new CountDownLatch(1);

    private ProbeExecutionTracker() {
    }

    static void reset() {
        EXECUTIONS.set(0);
        latch = new CountDownLatch(1);
    }

    static void recordExecution() {
        EXECUTIONS.incrementAndGet();
        latch.countDown();
    }

    static int executions() {
        return EXECUTIONS.get();
    }

    static CountDownLatch latch() {
        return latch;
    }
}
