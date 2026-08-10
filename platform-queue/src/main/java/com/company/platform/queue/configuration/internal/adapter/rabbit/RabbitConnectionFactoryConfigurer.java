package com.company.platform.queue.configuration.internal.adapter.rabbit;

import com.company.platform.queue.autoconfigure.properties.RabbitBrokerProperties;
import com.company.platform.queue.autoconfigure.properties.SslProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

public final class RabbitConnectionFactoryConfigurer {
    private RabbitConnectionFactoryConfigurer() {
    }

    public static CachingConnectionFactory create(
        RabbitBrokerProperties rabbit, boolean publisher
    ) {
        com.rabbitmq.client.ConnectionFactory client =
            new com.rabbitmq.client.ConnectionFactory();
        configureTls(client, rabbit.isTlsEnabled(), rabbit.getSsl());
        CachingConnectionFactory factory = new CachingConnectionFactory(client);
        factory.setAddresses(rabbit.getAddresses());
        factory.setVirtualHost(rabbit.getVirtualHost());
        factory.setUsername(rabbit.getUsername());
        factory.setPassword(rabbit.getPassword());
        factory.setConnectionTimeout(
            Math.toIntExact(rabbit.getConnectionTimeout().toMillis()));
        factory.setRequestedHeartBeat(
            Math.toIntExact(rabbit.getRequestedHeartbeat().toSeconds()));
        if (publisher) {
            factory.setPublisherConfirmType(
                CachingConnectionFactory.ConfirmType.CORRELATED);
            factory.setPublisherReturns(true);
        }
        return factory;
    }

    private static void configureTls(
        com.rabbitmq.client.ConnectionFactory client,
        boolean tlsEnabled,
        SslProperties ssl
    ) {
        if (!tlsEnabled) {
            return;
        }
        try {
            if (ssl.getTrustStoreLocation() == null
                && ssl.getKeyStoreLocation() == null) {
                client.useSslProtocol();
            } else {
                client.useSslProtocol(sslContext(ssl));
            }
            client.enableHostnameVerification();
        } catch (GeneralSecurityException | java.io.IOException exception) {
            throw new IllegalStateException(
                "Rabbit TLS initialization failed", exception);
        }
    }

    private static SSLContext sslContext(SslProperties ssl)
        throws GeneralSecurityException, java.io.IOException {
        TrustManager[] trustManagers = null;
        KeyManager[] keyManagers = null;
        if (ssl.getTrustStoreLocation() != null) {
            char[] password = chars(ssl.getTrustStorePassword());
            try {
                KeyStore store = load(ssl.getTrustStoreLocation(), password);
                TrustManagerFactory factory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
                factory.init(store);
                trustManagers = factory.getTrustManagers();
            } finally {
                Arrays.fill(password, '\0');
            }
        }
        if (ssl.getKeyStoreLocation() != null) {
            char[] storePassword = chars(ssl.getKeyStorePassword());
            char[] keyPassword = chars(
                ssl.getKeyPassword() == null
                    ? ssl.getKeyStorePassword() : ssl.getKeyPassword());
            try {
                KeyStore store = load(ssl.getKeyStoreLocation(), storePassword);
                KeyManagerFactory factory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
                factory.init(store, keyPassword);
                keyManagers = factory.getKeyManagers();
            } finally {
                Arrays.fill(storePassword, '\0');
                Arrays.fill(keyPassword, '\0');
            }
        }
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, trustManagers, null);
        return context;
    }

    private static KeyStore load(String location, char[] password)
        throws GeneralSecurityException, java.io.IOException {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream input = Files.newInputStream(path(location))) {
            store.load(input, password);
        }
        return store;
    }

    private static Path path(String location) {
        return location.startsWith("file:")
            ? Path.of(URI.create(location)) : Path.of(location);
    }

    private static char[] chars(String value) {
        return value == null ? new char[0] : value.toCharArray();
    }
}
