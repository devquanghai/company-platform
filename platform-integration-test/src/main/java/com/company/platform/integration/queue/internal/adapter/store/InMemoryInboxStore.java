package com.company.platform.integration.queue.internal.adapter.store;

import com.company.platform.queue.reliability.inbox.InboxAcquireResult;
import com.company.platform.queue.reliability.inbox.InboxAcquireStatus;
import com.company.platform.queue.reliability.inbox.InboxStore;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Integration-only fenced inbox. State is lost when the process restarts. */
public final class InMemoryInboxStore implements InboxStore {
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    @Override
    public synchronized InboxAcquireResult acquire(
        String consumerId, String messageId, Duration processingTimeout
    ) {
        String key = key(consumerId, messageId);
        Instant now = Instant.now();
        Entry current = entries.get(key);
        if (current != null && current.processed()) {
            return new InboxAcquireResult(
                InboxAcquireStatus.DUPLICATE_PROCESSED, null, current.token(), null);
        }
        if (current != null && current.lockedUntil().isAfter(now)) {
            return new InboxAcquireResult(
                InboxAcquireStatus.PROCESSING_BY_ANOTHER, current.owner(),
                current.token(), current.lockedUntil());
        }
        String owner = UUID.randomUUID().toString();
        long token = current == null ? 1 : current.token() + 1;
        Instant lockedUntil = now.plus(processingTimeout);
        entries.put(key, new Entry(owner, token, lockedUntil, false));
        return new InboxAcquireResult(
            current == null ? InboxAcquireStatus.ACQUIRED
                : InboxAcquireStatus.RETRYABLE_STALE_LOCK,
            owner, token, lockedUntil);
    }

    @Override
    public synchronized void renew(
        String consumerId, String messageId, String ownerId,
        long fencingToken, Duration processingTimeout
    ) {
        String key = key(consumerId, messageId);
        Entry current = owned(key, ownerId, fencingToken);
        if (current.processed()) {
            return;
        }
        entries.put(key, new Entry(
            ownerId, fencingToken, Instant.now().plus(processingTimeout), false));
    }

    @Override
    public synchronized void markProcessed(
        String consumerId, String messageId, String ownerId, long fencingToken
    ) {
        String key = key(consumerId, messageId);
        owned(key, ownerId, fencingToken);
        entries.put(key, new Entry(ownerId, fencingToken, Instant.EPOCH, true));
    }

    @Override
    public synchronized void markFailed(
        String consumerId, String messageId, String ownerId,
        long fencingToken, String failureCode
    ) {
        String key = key(consumerId, messageId);
        owned(key, ownerId, fencingToken);
        entries.remove(key);
    }

    private Entry owned(String key, String ownerId, long token) {
        Entry entry = entries.get(key);
        if (entry == null || !entry.owner().equals(ownerId) || entry.token() != token) {
            throw new IllegalStateException("stale integration inbox lease");
        }
        return entry;
    }

    private static String key(String consumerId, String messageId) {
        return consumerId + '\u0000' + messageId;
    }

    private record Entry(String owner, long token, Instant lockedUntil, boolean processed) { }
}
