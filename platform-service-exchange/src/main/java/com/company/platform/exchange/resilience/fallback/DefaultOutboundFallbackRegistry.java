package com.company.platform.exchange.resilience.fallback;

import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public final class DefaultOutboundFallbackRegistry implements OutboundFallbackRegistry {

    private final List<OutboundFallbackHandler<?>> handlers;

    public DefaultOutboundFallbackRegistry(List<OutboundFallbackHandler<?>> handlers) {
        this.handlers = List.copyOf(handlers == null ? List.of() : handlers);
        validateDuplicates();
    }

    @Override
    public <T> Optional<OutboundFallbackHandler<T>> find(
        FallbackContext context, Class<T> responseType
    ) {
        List<OutboundFallbackHandler<?>> matches = handlers.stream()
            .filter(handler -> handler.responseType().equals(responseType))
            .filter(handler -> annotationSupports(handler, context))
            .filter(handler -> handler.supports(context))
            .toList();
        if (matches.size() > 1) {
            throw new InvalidClientConfigurationException(
                context.getClientName(), "ambiguous fallback handlers");
        }
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        OutboundFallbackHandler<T> handler = (OutboundFallbackHandler<T>) matches.getFirst();
        return Optional.of(handler);
    }

    private static boolean annotationSupports(
        OutboundFallbackHandler<?> handler, FallbackContext context
    ) {
        ExchangeFallback annotation = handler.getClass().getAnnotation(ExchangeFallback.class);
        return annotation == null || (annotation.client().equals(context.getClientName())
            && (annotation.operation().equals("*")
                || annotation.operation().equals(context.getOperation())));
    }

    private void validateDuplicates() {
        java.util.Set<String> keys = new HashSet<>();
        for (OutboundFallbackHandler<?> handler : handlers) {
            ExchangeFallback annotation = handler.getClass().getAnnotation(ExchangeFallback.class);
            if (annotation == null) {
                continue;
            }
            String key = annotation.client() + "|" + annotation.operation()
                + "|" + handler.responseType().getName();
            if (!keys.add(key)) {
                throw new InvalidClientConfigurationException(
                    annotation.client(), "duplicate fallback registration");
            }
        }
    }
}
