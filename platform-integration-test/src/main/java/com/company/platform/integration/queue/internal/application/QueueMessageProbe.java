package com.company.platform.integration.queue.internal.application;

import com.company.platform.integration.queue.internal.domain.ConsumedQueueMessage;
import com.company.platform.integration.queue.internal.domain.QueueMode;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class QueueMessageProbe {
    private static final int MAX_MESSAGES_PER_MODE = 1_000;
    private final Map<QueueMode, Deque<ConsumedQueueMessage>> messages =
        new EnumMap<>(QueueMode.class);
    private final Map<QueueMode, Long> totals = new EnumMap<>(QueueMode.class);

    public QueueMessageProbe() {
        for (QueueMode mode : QueueMode.values()) {
            messages.put(mode, new ArrayDeque<>());
            totals.put(mode, 0L);
        }
    }

    public synchronized void record(QueueMode mode, ConsumedQueueMessage message) {
        Deque<ConsumedQueueMessage> values = messages.get(mode);
        while (values.size() >= MAX_MESSAGES_PER_MODE) {
            values.removeFirst();
        }
        values.addLast(message);
        totals.compute(mode, (ignored, total) -> total + 1);
    }

    public synchronized List<ConsumedQueueMessage> latest(QueueMode mode, int limit) {
        return messages.get(mode).reversed().stream().limit(limit).toList();
    }

    public synchronized long total(QueueMode mode) {
        return totals.get(mode);
    }

    public synchronized void clear(QueueMode mode) {
        messages.get(mode).clear();
        totals.put(mode, 0L);
    }
}
