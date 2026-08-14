package com.company.platform.exchange.api.client;

import java.util.Optional;

public interface ServiceExchangeClientRegistry {
    ServiceExchangeClient get(String name);

    Optional<ServiceExchangeClient> find(String name);

    boolean contains(String name);

    default <T extends ServiceExchangeClient> T get(String name, Class<T> clientType) {
        ServiceExchangeClient client = get(name);
        if (!clientType.isInstance(client)) {
            throw new IllegalArgumentException(
                "Client '" + name + "' is " + client.type()
                    + ", not " + clientType.getSimpleName());
        }
        return clientType.cast(client);
    }
}
