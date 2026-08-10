package com.company.platform.queue.topology.internal.adapter.kafka;

import com.company.platform.queue.topology.internal.port.out.QueueTopologyManager;
import com.company.platform.queue.topology.internal.port.out.TopologyProvisionResult;
import com.company.platform.queue.topology.internal.port.out.TopologyValidationResult;
import com.company.platform.queue.autoconfigure.properties.DestinationProperties;
import com.company.platform.queue.autoconfigure.properties.PlatformQueueProperties;
import com.company.platform.queue.domain.model.QueueProviderType;
import com.company.platform.queue.configuration.internal.adapter.kafka.KafkaSecurityConfiguration;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class KafkaQueueTopologyManager implements QueueTopologyManager {
    private static final String INVALID = "QUEUE.KAFKA_TOPOLOGY_INVALID";
    private static final String PROVISION_FAILED =
        "QUEUE.KAFKA_TOPOLOGY_PROVISION_FAILED";
    private final PlatformQueueProperties properties;

    public KafkaQueueTopologyManager(PlatformQueueProperties properties) {
        this.properties = properties;
    }

    @Override
    public TopologyValidationResult validate() {
        List<String> errors = new ArrayList<>();
        destinationsByBroker().forEach((brokerName, destinations) -> {
            try (AdminClient admin = admin(brokerName)) {
                var names = destinations.stream()
                    .map(entry -> entry.getValue().getKafka().getTopic()).toList();
                var descriptions = admin.describeTopics(names).allTopicNames()
                    .get(timeout(brokerName).toMillis(), TimeUnit.MILLISECONDS);
                destinations.forEach(entry -> {
                    var configured = entry.getValue().getKafka();
                    var actual = descriptions.get(configured.getTopic());
                    if (actual == null
                        || actual.partitions().size() < configured.getPartitions()) {
                        errors.add(INVALID + ":" + entry.getKey());
                    }
                });
            } catch (Exception exception) {
                errors.add(INVALID + ":" + brokerName);
            }
        });
        return new TopologyValidationResult(errors.isEmpty(), errors);
    }

    @Override
    public TopologyProvisionResult provision() {
        int[] counts = new int[2];
        List<String> errors = new ArrayList<>();
        destinationsByBroker().forEach((brokerName, destinations) -> {
            try (AdminClient admin = admin(brokerName)) {
                var existing = admin.listTopics().names()
                    .get(timeout(brokerName).toMillis(), TimeUnit.MILLISECONDS);
                List<NewTopic> missing = destinations.stream()
                    .filter(entry -> !existing.contains(
                        entry.getValue().getKafka().getTopic()))
                    .map(entry -> new NewTopic(
                        entry.getValue().getKafka().getTopic(),
                        entry.getValue().getKafka().getPartitions(),
                        entry.getValue().getKafka().getReplicationFactor()))
                    .toList();
                counts[1] += destinations.size() - missing.size();
                if (!missing.isEmpty()) {
                    admin.createTopics(missing).all()
                        .get(timeout(brokerName).toMillis(), TimeUnit.MILLISECONDS);
                    counts[0] += missing.size();
                }
            } catch (Exception exception) {
                errors.add(PROVISION_FAILED + ":" + brokerName);
            }
        });
        return new TopologyProvisionResult(counts[0], counts[1], errors);
    }

    private Map<String, List<Map.Entry<String, DestinationProperties>>>
        destinationsByBroker() {
        Map<String, List<Map.Entry<String, DestinationProperties>>> result =
            new LinkedHashMap<>();
        properties.getDestinations().entrySet().stream()
            .filter(entry -> entry.getValue().isEnabled())
            .filter(entry -> {
                var broker = properties.getBrokers().get(
                    entry.getValue().getBroker());
                return broker != null && broker.isEnabled()
                    && broker.getProvider() == QueueProviderType.KAFKA;
            })
            .forEach(entry -> result.computeIfAbsent(
                entry.getValue().getBroker(), ignored -> new ArrayList<>()).add(entry));
        return result;
    }

    private AdminClient admin(String brokerName) {
        var kafka = properties.getBrokers().get(brokerName).getKafka();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            kafka.getBootstrapServers());
        config.put(AdminClientConfig.CLIENT_ID_CONFIG,
            kafka.getClientIdPrefix() + "-admin-" + brokerName);
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,
            Math.toIntExact(kafka.getRequestTimeout().toMillis()));
        KafkaSecurityConfiguration.apply(config, kafka);
        return AdminClient.create(config);
    }

    private Duration timeout(String brokerName) {
        return properties.getBrokers().get(brokerName)
            .getKafka().getRequestTimeout();
    }
}
