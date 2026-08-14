package com.company.platform.exchange.client.internal.application;

import com.company.platform.exchange.autoconfigure.properties.ClientProperties;
import com.company.platform.exchange.autoconfigure.properties.ServiceExchangeProperties;
import com.company.platform.exchange.domain.exception.ClientDisabledException;
import com.company.platform.exchange.domain.exception.ClientNotFoundException;
import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.api.client.ServiceExchangeClientType;

import java.util.Map;
import java.util.Objects;

public final class ClientConfigurationResolver {

    private final Map<String, ClientProperties> clients;

    public ClientConfigurationResolver(ServiceExchangeProperties properties) {
        this.clients = Map.copyOf(properties.getClients());
    }

    public ClientProperties resolve(String clientName) {
        Objects.requireNonNull(clientName, "clientName");
        ClientProperties client = clients.get(clientName);
        if (client == null) {
            throw new ClientNotFoundException(clientName);
        }
        if (!client.isEnabled()) {
            throw new ClientDisabledException(clientName);
        }
        return client;
    }

    public ClientProperties resolve(String clientName, ExchangeProtocol expectedProtocol) {
        ClientProperties client = resolve(clientName);
        ExchangeProtocol actual = client.getType() == ServiceExchangeClientType.GRPC
            ? ExchangeProtocol.GRPC : ExchangeProtocol.HTTP;
        if (actual != expectedProtocol) {
            throw new InvalidClientConfigurationException(
                clientName, "expected protocol " + expectedProtocol);
        }
        return client;
    }

    public Map<String, ClientProperties> clients() {
        return clients;
    }

    public String resilienceInstance(String clientName) {
        ClientProperties client = resolve(clientName);
        return client.getResilienceInstance() == null
            || client.getResilienceInstance().isBlank()
            ? clientName : client.getResilienceInstance();
    }
}
