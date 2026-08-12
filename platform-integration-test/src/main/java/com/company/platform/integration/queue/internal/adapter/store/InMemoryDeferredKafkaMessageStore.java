package com.company.platform.integration.queue.internal.adapter.store;

import com.company.platform.queue.api.kafka.DeferredKafkaBatch;
import com.company.platform.queue.api.kafka.DeferredKafkaMessage;
import com.company.platform.queue.api.kafka.DeferredKafkaMessageStore;
import com.company.platform.queue.api.kafka.DeferredKafkaStageResult;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/**
 * Single-process integration store backed by a forced append-only local WAL.
 * Production deployments must replace it with a transactional shared database.
 */
public final class InMemoryDeferredKafkaMessageStore implements DeferredKafkaMessageStore {
    private static final int SNAPSHOT_MAGIC = 0x51444546;
    private static final int SNAPSHOT_VERSION = 1;
    private static final byte UPSERT = 1;
    private static final byte DELETE = 2;
    private static final byte UPDATE_DELIVERY = 3;
    private static final int MAX_WAL_TRANSACTION_BYTES = 256 * 1024 * 1024;
    private final Map<PartitionKey, NavigableMap<Long, Stored>> partitions =
        new LinkedHashMap<>();
    private final Map<String, Claim> claims = new LinkedHashMap<>();
    private final AtomicLong fencing = new AtomicLong();
    private final Path walPath;
    private boolean walWritable = true;

    public InMemoryDeferredKafkaMessageStore(Path walPath) {
        this.walPath = Objects.requireNonNull(walPath, "walPath");
        loadWal();
    }

    @Override
    public synchronized DeferredKafkaStageResult stage(DeferredKafkaMessage message) {
        MessagePosition position = position(message);
        NavigableMap<Long, Stored> records = partitions.computeIfAbsent(
            position.partition(), ignored -> new TreeMap<>());
        if (records.containsKey(position.offset())) {
            return DeferredKafkaStageResult.DUPLICATE;
        }
        Stored stored = new Stored(message, 1, null, null);
        appendTransaction(List.of(WalOperation.upsert(
            position.partition(), position.offset(), stored)));
        records.put(position.offset(), stored);
        return DeferredKafkaStageResult.STAGED;
    }

    @Override
    public synchronized Optional<DeferredKafkaBatch> claimReady(
        String subscription, int maximumMessages, Duration maximumWait,
        Duration lockTimeout, Instant now
    ) {
        releaseExpired(now);
        for (var partitionEntry : partitions.entrySet()) {
            if (!partitionEntry.getKey().subscription().equals(subscription)
                || partitionEntry.getValue().isEmpty()) {
                continue;
            }
            NavigableMap<Long, Stored> records = partitionEntry.getValue();
            Stored head = records.firstEntry().getValue();
            if (head.claimId() != null
                || head.retryAt() != null && head.retryAt().isAfter(now)) {
                continue;
            }
            boolean countReady = records.size() >= maximumMessages;
            boolean ageReady = !head.message().context().receivedAt()
                .plus(maximumWait).isAfter(now);
            if (!countReady && !ageReady) {
                continue;
            }
            List<Long> offsets = contiguousReadyOffsets(records, maximumMessages, now);
            if (offsets.isEmpty()) {
                continue;
            }
            String claimId = UUID.randomUUID().toString();
            String ownerId = UUID.randomUUID().toString();
            long token = fencing.incrementAndGet();
            int attempt = offsets.stream().map(records::get)
                .mapToInt(Stored::attempt).max().orElse(1);
            offsets.forEach(offset -> {
                Stored value = records.get(offset);
                records.put(offset, value.withClaim(claimId));
            });
            claims.put(claimId, new Claim(
                ownerId, token, partitionEntry.getKey(), offsets,
                now.plus(lockTimeout), attempt));
            List<DeferredKafkaMessage> messages = offsets.stream()
                .map(records::get).map(Stored::message).toList();
            return Optional.of(new DeferredKafkaBatch(
                claimId, ownerId, token, attempt, messages));
        }
        return Optional.empty();
    }

    @Override
    public synchronized void renewClaim(
        String claimId, String ownerId, long fencingToken, Duration lockTimeout
    ) {
        Claim claim = owned(claimId, ownerId, fencingToken);
        claims.put(claimId, claim.withExpiry(Instant.now().plus(lockTimeout)));
    }

    @Override
    public synchronized void markCompleted(
        String claimId, String ownerId, long fencingToken
    ) {
        Claim claim = owned(claimId, ownerId, fencingToken);
        NavigableMap<Long, Stored> records = partitions.get(claim.partition());
        appendTransaction(claim.offsets().stream()
            .map(offset -> WalOperation.delete(claim.partition(), offset)).toList());
        claim.offsets().forEach(records::remove);
        finishClaim(claimId, claim.partition(), records);
    }

    @Override
    public synchronized void release(
        String claimId, String ownerId, long fencingToken,
        Instant retryAt, String failureCode
    ) {
        Claim claim = owned(claimId, ownerId, fencingToken);
        NavigableMap<Long, Stored> records = partitions.get(claim.partition());
        List<WalOperation> operations = claim.offsets().stream().map(offset -> {
            Stored value = records.get(offset);
            return WalOperation.update(claim.partition(), offset, new Stored(
                value.message(), value.attempt() + 1, retryAt, null));
        }).toList();
        appendTransaction(operations);
        operations.forEach(operation -> records.put(operation.offset(), operation.stored()));
        claims.remove(claimId);
    }

    @Override
    public synchronized void releaseContended(
        String claimId, String ownerId, long fencingToken, Instant retryAt
    ) {
        Claim claim = owned(claimId, ownerId, fencingToken);
        NavigableMap<Long, Stored> records = partitions.get(claim.partition());
        List<WalOperation> operations = claim.offsets().stream().map(offset -> {
            Stored value = records.get(offset);
            return WalOperation.update(claim.partition(), offset,
                new Stored(value.message(), value.attempt(), retryAt, null));
        }).toList();
        appendTransaction(operations);
        operations.forEach(operation -> records.put(operation.offset(), operation.stored()));
        claims.remove(claimId);
    }

    @Override
    public synchronized void markDeadLetter(
        String claimId, String ownerId, long fencingToken,
        int successfullyProcessedMessages, String failureCode
    ) {
        Claim claim = owned(claimId, ownerId, fencingToken);
        NavigableMap<Long, Stored> records = partitions.get(claim.partition());
        int terminalCount = Math.min(
            claim.offsets().size(), successfullyProcessedMessages + 1);
        List<WalOperation> operations = new ArrayList<>();
        claim.offsets().subList(0, terminalCount).forEach(offset ->
            operations.add(WalOperation.delete(claim.partition(), offset)));
        claim.offsets().subList(terminalCount, claim.offsets().size()).forEach(offset -> {
            Stored value = records.get(offset);
            operations.add(WalOperation.update(claim.partition(), offset,
                new Stored(value.message(), value.attempt(), null, null)));
        });
        appendTransaction(operations);
        claim.offsets().subList(0, terminalCount).forEach(records::remove);
        operations.stream().filter(operation -> operation.type() == UPDATE_DELIVERY)
            .forEach(operation -> records.put(operation.offset(), operation.stored()));
        finishClaim(claimId, claim.partition(), records);
    }

    private void releaseExpired(Instant now) {
        List<String> expired = claims.entrySet().stream()
            .filter(entry -> !entry.getValue().expiresAt().isAfter(now))
            .map(Map.Entry::getKey).toList();
        for (String claimId : expired) {
            Claim claim = claims.get(claimId);
            NavigableMap<Long, Stored> records = partitions.get(claim.partition());
            List<WalOperation> operations = claim.offsets().stream().map(offset -> {
                Stored value = records.get(offset);
                return WalOperation.update(claim.partition(), offset,
                    new Stored(value.message(), value.attempt() + 1, now, null));
            }).toList();
            appendTransaction(operations);
            operations.forEach(operation ->
                records.put(operation.offset(), operation.stored()));
            claims.remove(claimId);
        }
    }

    private static List<Long> contiguousReadyOffsets(
        NavigableMap<Long, Stored> records, int maximum, Instant now
    ) {
        List<Long> result = new ArrayList<>();
        Long previous = null;
        for (var entry : records.entrySet()) {
            Stored value = entry.getValue();
            if (result.size() >= maximum || value.claimId() != null
                || value.retryAt() != null && value.retryAt().isAfter(now)
                || previous != null && entry.getKey() != previous + 1) {
                break;
            }
            result.add(entry.getKey());
            previous = entry.getKey();
        }
        return result;
    }

    private Claim owned(String claimId, String ownerId, long token) {
        Claim claim = claims.get(claimId);
        if (claim == null || !claim.ownerId().equals(ownerId)
            || claim.fencingToken() != token) {
            throw new IllegalStateException("stale integration deferred claim");
        }
        return claim;
    }

    private void finishClaim(
        String claimId, PartitionKey partition, NavigableMap<Long, Stored> records
    ) {
        claims.remove(claimId);
        if (records.isEmpty()) {
            partitions.remove(partition);
        }
    }

    private static MessagePosition position(DeferredKafkaMessage message) {
        if (message.context().partition() == null || message.context().offset() == null) {
            throw new IllegalArgumentException("Kafka partition and offset are required");
        }
        return new MessagePosition(new PartitionKey(
            message.subscription(), message.context().physicalDestination(),
            message.context().partition()), message.context().offset());
    }

    private void appendTransaction(List<WalOperation> operations) {
        if (operations.isEmpty()) {
            return;
        }
        if (!walWritable) {
            throw new IllegalStateException(
                "integration deferred queue WAL requires process restart");
        }
        Path parent = walPath.getParent();
        try {
            Files.createDirectories(parent);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.writeInt(operations.size());
                for (WalOperation operation : operations) {
                    writeOperation(output, operation);
                }
            }
            byte[] payload = buffer.toByteArray();
            if (payload.length > MAX_WAL_TRANSACTION_BYTES) {
                throw new IllegalStateException(
                    "integration deferred queue WAL transaction is too large");
            }
            CRC32 checksum = new CRC32();
            checksum.update(payload);
            try (RandomAccessFile wal = new RandomAccessFile(walPath.toFile(), "rw")) {
                long originalLength = wal.length();
                try {
                    if (originalLength == 0) {
                        wal.writeInt(SNAPSHOT_MAGIC);
                        wal.writeInt(SNAPSHOT_VERSION);
                    }
                    wal.seek(wal.length());
                    wal.writeInt(payload.length);
                    wal.writeInt((int) checksum.getValue());
                    wal.write(payload);
                    wal.getFD().sync();
                } catch (IOException appendFailure) {
                    try {
                        wal.setLength(originalLength);
                        wal.getFD().sync();
                    } catch (IOException rollbackFailure) {
                        walWritable = false;
                        appendFailure.addSuppressed(rollbackFailure);
                    }
                    throw appendFailure;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                "integration deferred queue WAL write failed", exception);
        }
    }

    private void loadWal() {
        if (!Files.exists(walPath) || fileIsEmpty(walPath)) {
            return;
        }
        try (RandomAccessFile wal = new RandomAccessFile(walPath.toFile(), "rw")) {
            if (wal.readInt() != SNAPSHOT_MAGIC || wal.readInt() != SNAPSHOT_VERSION) {
                throw new IOException("unsupported snapshot format");
            }
            long validLength = wal.getFilePointer();
            while (validLength < wal.length()) {
                if (wal.length() - validLength < Integer.BYTES * 2L) {
                    wal.setLength(validLength);
                    break;
                }
                int length = wal.readInt();
                int expectedChecksum = wal.readInt();
                if (length < 1 || length > MAX_WAL_TRANSACTION_BYTES) {
                    throw new IOException("invalid WAL transaction length");
                }
                if (wal.length() - wal.getFilePointer() < length) {
                    wal.setLength(validLength);
                    break;
                }
                byte[] payload = new byte[length];
                wal.readFully(payload);
                CRC32 checksum = new CRC32();
                checksum.update(payload);
                if ((int) checksum.getValue() != expectedChecksum) {
                    throw new IOException("invalid WAL transaction checksum");
                }
                replayTransaction(payload);
                validLength = wal.getFilePointer();
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                "integration deferred queue WAL read failed", exception);
        }
    }

    private static boolean fileIsEmpty(Path path) {
        try {
            return Files.size(path) == 0;
        } catch (IOException exception) {
            throw new IllegalStateException("integration deferred queue WAL stat failed", exception);
        }
    }

    private void replayTransaction(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(
            new ByteArrayInputStream(payload))) {
            int count = input.readInt();
            if (count < 1 || count > 1_000_000) {
                throw new IOException("invalid WAL operation count");
            }
            for (int index = 0; index < count; index++) {
                byte type = input.readByte();
                if (type == UPSERT) {
                    LoadedStored loaded = readStored(input);
                    MessagePosition position = position(loaded.stored().message());
                    partitions.computeIfAbsent(position.partition(), ignored -> new TreeMap<>())
                        .put(position.offset(), loaded.stored());
                } else if (type == DELETE) {
                    PartitionKey partition = new PartitionKey(
                        input.readUTF(), input.readUTF(), input.readInt());
                    long offset = input.readLong();
                    NavigableMap<Long, Stored> records = partitions.get(partition);
                    if (records != null) {
                        records.remove(offset);
                        if (records.isEmpty()) {
                            partitions.remove(partition);
                        }
                    }
                } else if (type == UPDATE_DELIVERY) {
                    PartitionKey partition = readPartitionKey(input);
                    long offset = input.readLong();
                    int attempt = input.readInt();
                    Instant retryAt = readInstant(input);
                    NavigableMap<Long, Stored> records = partitions.get(partition);
                    Stored current = records == null ? null : records.get(offset);
                    if (current == null) {
                        throw new IOException("WAL update references missing record");
                    }
                    records.put(offset, new Stored(
                        current.message(), attempt, retryAt, null));
                } else {
                    throw new IOException("invalid WAL operation type");
                }
            }
        }
    }

    private static void writeOperation(DataOutputStream output, WalOperation operation)
        throws IOException {
        output.writeByte(operation.type());
        if (operation.type() == UPSERT) {
            writeStored(output, operation.stored(), operation.offset());
            return;
        }
        writePartitionKey(output, operation.partition());
        output.writeLong(operation.offset());
        if (operation.type() == UPDATE_DELIVERY) {
            output.writeInt(operation.stored().attempt());
            writeInstant(output, operation.stored().retryAt());
        }
    }

    private static void writePartitionKey(DataOutputStream output, PartitionKey partition)
        throws IOException {
        output.writeUTF(partition.subscription());
        output.writeUTF(partition.topic());
        output.writeInt(partition.partition());
    }

    private static PartitionKey readPartitionKey(DataInputStream input) throws IOException {
        return new PartitionKey(input.readUTF(), input.readUTF(), input.readInt());
    }

    private static void writeStored(
        DataOutputStream output, Stored stored, long offset
    ) throws IOException {
        DeferredKafkaMessage message = stored.message();
        var context = message.context();
        output.writeUTF(message.subscription());
        writeNullable(output, message.messageKey());
        byte[] body = message.body();
        output.writeInt(body.length);
        output.write(body);
        output.writeUTF(context.provider().name());
        writeNullable(output, context.broker());
        writeNullable(output, context.subscription());
        writeNullable(output, context.destination());
        writeNullable(output, context.physicalDestination());
        writeNullable(output, context.messageId());
        writeNullable(output, context.correlationId());
        writeNullable(output, context.causationId());
        output.writeInt(context.headers().size());
        for (var header : context.headers().entrySet()) {
            output.writeUTF(header.getKey());
            output.writeUTF(header.getValue());
        }
        writeInstant(output, context.receivedAt());
        output.writeInt(context.deliveryAttempt());
        output.writeInt(context.partition());
        output.writeLong(offset);
        writeNullable(output, context.consumerGroup());
        writeNullable(output, context.exchange());
        writeNullable(output, context.routingKey());
        output.writeBoolean(context.redelivered());
        writeNullable(output, context.traceId());
        output.writeInt(stored.attempt());
        writeInstant(output, stored.retryAt());
    }

    private static LoadedStored readStored(DataInputStream input) throws IOException {
        String subscription = input.readUTF();
        String key = readNullable(input);
        int bodyLength = input.readInt();
        if (bodyLength < 1 || bodyLength > 2_097_152) {
            throw new IOException("invalid snapshot body length");
        }
        byte[] body = input.readNBytes(bodyLength);
        if (body.length != bodyLength) {
            throw new EOFException("truncated snapshot body");
        }
        var provider = com.company.platform.queue.domain.model.QueueProviderType.valueOf(
            input.readUTF());
        String broker = readNullable(input);
        String contextSubscription = readNullable(input);
        String destination = readNullable(input);
        String physicalDestination = readNullable(input);
        String messageId = readNullable(input);
        String correlationId = readNullable(input);
        String causationId = readNullable(input);
        int headerCount = input.readInt();
        if (headerCount < 0 || headerCount > 64) {
            throw new IOException("invalid snapshot header count");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (int index = 0; index < headerCount; index++) {
            headers.put(input.readUTF(), input.readUTF());
        }
        Instant receivedAt = readInstant(input);
        int deliveryAttempt = input.readInt();
        int partition = input.readInt();
        long offset = input.readLong();
        String consumerGroup = readNullable(input);
        String exchange = readNullable(input);
        String routingKey = readNullable(input);
        boolean redelivered = input.readBoolean();
        String traceId = readNullable(input);
        int attempt = input.readInt();
        Instant retryAt = readInstant(input);
        var context = new com.company.platform.queue.api.consume.MessageContext(
            provider, broker, contextSubscription, destination, physicalDestination,
            messageId, correlationId, causationId, headers, receivedAt,
            deliveryAttempt, partition, offset, consumerGroup, exchange, routingKey,
            redelivered, traceId);
        var message = new DeferredKafkaMessage(subscription, key, body, context);
        return new LoadedStored(new Stored(message, attempt, retryAt, null));
    }

    private static void writeNullable(DataOutputStream output, String value)
        throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeUTF(value);
        }
    }

    private static String readNullable(DataInputStream input) throws IOException {
        return input.readBoolean() ? input.readUTF() : null;
    }

    private static void writeInstant(DataOutputStream output, Instant value)
        throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeLong(value.getEpochSecond());
            output.writeInt(value.getNano());
        }
    }

    private static Instant readInstant(DataInputStream input) throws IOException {
        return input.readBoolean()
            ? Instant.ofEpochSecond(input.readLong(), input.readInt()) : null;
    }

    private record MessagePosition(PartitionKey partition, long offset) { }
    private record LoadedStored(Stored stored) { }
    private record WalOperation(
        byte type, PartitionKey partition, long offset, Stored stored
    ) {
        static WalOperation upsert(PartitionKey partition, long offset, Stored stored) {
            return new WalOperation(UPSERT, partition, offset, stored);
        }
        static WalOperation delete(PartitionKey partition, long offset) {
            return new WalOperation(DELETE, partition, offset, null);
        }
        static WalOperation update(PartitionKey partition, long offset, Stored stored) {
            return new WalOperation(UPDATE_DELIVERY, partition, offset, stored);
        }
    }
    private record PartitionKey(String subscription, String topic, int partition) { }
    private record Stored(
        DeferredKafkaMessage message, int attempt, Instant retryAt, String claimId
    ) {
        Stored withClaim(String value) {
            return new Stored(message, attempt, retryAt, value);
        }
    }
    private record Claim(
        String ownerId, long fencingToken, PartitionKey partition,
        List<Long> offsets, Instant expiresAt, int attempt
    ) {
        Claim {
            offsets = List.copyOf(offsets);
        }
        Claim withExpiry(Instant value) {
            return new Claim(ownerId, fencingToken, partition, offsets, value, attempt);
        }
    }
}
