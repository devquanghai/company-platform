package com.company.platform.queue.configuration.internal.adapter.kafka;

import com.company.platform.queue.autoconfigure.properties.KafkaBrokerProperties;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;

import java.util.Map;

public final class KafkaSecurityConfiguration {
    private KafkaSecurityConfiguration() {
    }

    public static void apply(
        Map<String, Object> config, KafkaBrokerProperties kafka
    ) {
        config.put("security.protocol", kafka.getSecurityProtocol());
        var ssl = kafka.getSsl();
        put(config, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG,
            ssl.getTrustStoreLocation());
        put(config, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG,
            ssl.getTrustStorePassword());
        put(config, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG,
            ssl.getKeyStoreLocation());
        put(config, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG,
            ssl.getKeyStorePassword());
        put(config, SslConfigs.SSL_KEY_PASSWORD_CONFIG, ssl.getKeyPassword());
        config.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG,
            "https");
        if (kafka.getSaslMechanism() != null) {
            config.put(SaslConfigs.SASL_MECHANISM, kafka.getSaslMechanism());
            if (kafka.getUsername() != null || kafka.getPassword() != null) {
                config.put(SaslConfigs.SASL_JAAS_CONFIG, jaas(kafka));
            }
        }
    }

    private static String jaas(KafkaBrokerProperties kafka) {
        String mechanism = kafka.getSaslMechanism().toUpperCase();
        String module = switch (mechanism) {
            case "PLAIN" ->
                "org.apache.kafka.common.security.plain.PlainLoginModule";
            case "SCRAM-SHA-256", "SCRAM-SHA-512" ->
                "org.apache.kafka.common.security.scram.ScramLoginModule";
            default -> throw new IllegalArgumentException(
                "SASL credentials require PLAIN or SCRAM mechanism");
        };
        return module + " required username=\"" + escape(kafka.getUsername())
            + "\" password=\"" + escape(kafka.getPassword()) + "\";";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void put(
        Map<String, Object> config, String name, String value
    ) {
        if (value != null && !value.isBlank()) {
            config.put(name, value);
        }
    }
}
