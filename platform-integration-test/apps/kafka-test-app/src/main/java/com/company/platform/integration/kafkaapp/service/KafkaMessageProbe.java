package com.company.platform.integration.kafkaapp.service;

import com.company.platform.integration.kafkaapp.api.ReceivedKafkaMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Service
public class KafkaMessageProbe {
    private static final int MAX_MESSAGES = 1000;

    private final Deque<ReceivedKafkaMessage> messages = new ArrayDeque<>();
    private long totalReceived;

    public synchronized void record(ReceivedKafkaMessage message) {
        while (messages.size() >= MAX_MESSAGES) {
            messages.pollFirst();
        }
        messages.addLast(message);
        totalReceived++;
    }

    public synchronized List<ReceivedKafkaMessage> latest(int limit) {
        return messages.reversed().stream().limit(limit).toList();
    }

    public synchronized ReceivedKafkaMessage find(String messageId) {
        return messages.stream()
            .filter(message -> messageId.equals(message.messageId()))
            .findFirst()
            .orElse(null);
    }

    public synchronized long totalReceived() {
        return totalReceived;
    }

    public synchronized void clear() {
        messages.clear();
        totalReceived = 0L;
    }
}
