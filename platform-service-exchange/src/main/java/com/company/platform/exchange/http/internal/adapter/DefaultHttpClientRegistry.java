package com.company.platform.exchange.http.internal.adapter;

import com.company.platform.exchange.api.http.HttpClientRegistry;
import com.company.platform.exchange.client.internal.application.ClientConfigurationResolver;
import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.autoconfigure.properties.ProxyProperties;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.domain.model.ProxyCustomizationContext;
import com.company.platform.exchange.domain.model.ProxyEndpoint;
import com.company.platform.exchange.domain.policy.ClientProxyCustomizer;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultHttpClientRegistry implements HttpClientRegistry, AutoCloseable {

    private final ClientConfigurationResolver resolver;
    private final SslBundles sslBundles;
    private final ClientProxyCustomizer proxyCustomizer;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public DefaultHttpClientRegistry(
        ClientConfigurationResolver resolver, Optional<SslBundles> sslBundles
    ) {
        this(resolver, sslBundles, (context, configured) -> configured);
    }

    public DefaultHttpClientRegistry(
        ClientConfigurationResolver resolver, Optional<SslBundles> sslBundles,
        ClientProxyCustomizer proxyCustomizer
    ) {
        this.resolver = resolver;
        this.sslBundles = sslBundles.orElse(null);
        this.proxyCustomizer = proxyCustomizer;
    }

    @Override
    public RestClient getClient(String clientName) {
        ClientProperties client = resolver.resolve(clientName, ExchangeProtocol.HTTP);
        return entries.computeIfAbsent(clientName, ignored -> create(clientName, client)).restClient();
    }

    private Entry create(String name, ClientProperties client) {
        try {
            var http = client.getHttp();
            var pool = http.getPool();
            var managerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(pool.getMaxTotal())
                .setMaxConnPerRoute(pool.getMaxPerRoute())
                .setConnectionTimeToLive(TimeValue.of(pool.getTimeToLive()))
                .setValidateAfterInactivity(TimeValue.of(pool.getValidateAfterInactivity()))
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.of(http.getConnectTimeout()))
                    .build());
            if (client.getSsl().isEnabled()) {
                SSLContext sslContext = sslContext(client);
                var tls = ClientTlsStrategyBuilder.create().setSslContext(sslContext);
                if (!client.getSsl().getProtocols().isEmpty()) {
                    tls.setTlsVersions(client.getSsl().getProtocols().toArray(String[]::new));
                }
                if (!client.getSsl().getCipherSuites().isEmpty()) {
                    tls.setCiphers(client.getSsl().getCipherSuites().toArray(String[]::new));
                }
                if (!client.getSsl().isHostnameVerificationEnabled()) {
                    tls.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                }
                managerBuilder.setTlsSocketStrategy(tls.buildClassic());
            }
            var manager = managerBuilder.build();
            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(http.getConnectionRequestTimeout()))
                .setResponseTimeout(Timeout.of(http.getResponseTimeout()))
                .build();
            var builder = HttpClients.custom()
                .setConnectionManager(manager)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.of(pool.getEvictIdleConnectionsAfter()));
            configureProxy(name, http.getBaseUrl(), client.getProxy(), builder);
            CloseableHttpClient apache = builder.build();
            RestClient.Builder rest = RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(apache))
                .baseUrl(http.getBaseUrl());
            http.getDefaultHeaders().forEach(rest::defaultHeader);
            return new Entry(rest.build(), apache);
        } catch (Exception exception) {
            throw new com.company.platform.exchange.domain.exception.InvalidClientConfigurationException(
                name, "HTTP transport creation failed: " + exception.getClass().getSimpleName());
        }
    }

    private SSLContext sslContext(ClientProperties client) throws Exception {
        if (client.getSsl().isTrustAll()) {
            TrustManager[] trustManagers = {new TrustAllManager()};
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustManagers, new SecureRandom());
            return context;
        }
        if (StringUtils.hasText(client.getSsl().getBundle())) {
            return sslBundles.getBundle(client.getSsl().getBundle()).createSslContext();
        }
        return SSLContext.getDefault();
    }

    private void configureProxy(
        String clientName, String target, ProxyProperties proxy,
        org.apache.hc.client5.http.impl.classic.HttpClientBuilder builder
    ) {
        if (!proxy.isEnabled()) {
            return;
        }
        ProxyEndpoint configured = ProxyEndpoint.builder()
            .scheme(proxy.getScheme()).host(proxy.getHost()).port(proxy.getPort())
            .username(proxy.getUsername()).password(proxy.getPassword()).build();
        ProxyEndpoint customized = proxyCustomizer.customize(
            ProxyCustomizationContext.builder().clientName(clientName)
                .protocol(ExchangeProtocol.HTTP).target(target).build(), configured);
        if (customized == null) {
            return;
        }
        HttpHost host = new HttpHost(
            customized.getScheme(), customized.getHost(), customized.getPort());
        builder.setRoutePlanner(new DefaultProxyRoutePlanner(host) {
            @Override
            protected HttpHost determineProxy(
                HttpHost targetHost, HttpContext context
            ) throws HttpException {
                return isNonProxyHost(targetHost.getHostName(), proxy.getNonProxyHosts())
                    ? null : super.determineProxy(targetHost, context);
            }
        });
        if (StringUtils.hasText(customized.getUsername())) {
            BasicCredentialsProvider credentials = new BasicCredentialsProvider();
            credentials.setCredentials(
                new AuthScope(host),
                new UsernamePasswordCredentials(
                    customized.getUsername(),
                    customized.getPassword() == null
                        ? new char[0] : customized.getPassword().toCharArray()));
            builder.setDefaultCredentialsProvider(credentials);
        }
    }

    private static boolean isNonProxyHost(String host, java.util.List<String> patterns) {
        if (host == null) {
            return false;
        }
        String candidate = host.toLowerCase(java.util.Locale.ROOT);
        return patterns.stream().filter(StringUtils::hasText).anyMatch(pattern -> {
            String normalized = pattern.toLowerCase(java.util.Locale.ROOT);
            return normalized.equals(candidate)
                || normalized.startsWith("*.")
                    && (candidate.equals(normalized.substring(2))
                        || candidate.endsWith(normalized.substring(1)));
        });
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (Entry entry : entries.values()) {
            try {
                entry.apache().close();
            } catch (IOException exception) {
                failure = exception;
            }
        }
        entries.clear();
        if (failure != null) {
            throw failure;
        }
    }

    private static final class Entry {
        private final RestClient restClient;
        private final CloseableHttpClient apache;

        private Entry(RestClient restClient, CloseableHttpClient apache) {
            this.restClient = restClient;
            this.apache = apache;
        }
        private RestClient restClient() { return restClient; }
        private CloseableHttpClient apache() { return apache; }
    }

    private static final class TrustAllManager implements X509TrustManager {
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
    }
}
