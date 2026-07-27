package com.company.platform.exchange;

import com.company.platform.exchange.domain.exception.InvalidClientConfigurationException;
import com.company.platform.exchange.domain.model.ExchangeProtocol;
import com.company.platform.exchange.resilience.fallback.DefaultOutboundFallbackRegistry;
import com.company.platform.exchange.resilience.fallback.ExchangeFallback;
import com.company.platform.exchange.resilience.fallback.FallbackContext;
import com.company.platform.exchange.resilience.fallback.OutboundFallbackHandler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FallbackRegistryTest {

    @Test
    void selectsByClientOperationTypeAndSupports() {
        DefaultOutboundFallbackRegistry registry =
            new DefaultOutboundFallbackRegistry(List.of(new PaymentFallback()));
        FallbackContext matching = context("payment-rest", "POST /payments", String.class);
        FallbackContext other = context("other", "POST /payments", String.class);

        assertThat(registry.find(matching, String.class)).isPresent()
            .get().extracting(handler -> handler.fallback(matching)).isEqualTo("pending");
        assertThat(registry.find(other, String.class)).isEmpty();
        assertThat(registry.find(matching, Integer.class)).isEmpty();
    }

    @Test
    void detectsDuplicateRegistrationsAndAmbiguousUnannotatedHandlers() {
        assertThatThrownBy(() -> new DefaultOutboundFallbackRegistry(
            List.of(new PaymentFallback(), new PaymentFallback())))
            .isInstanceOf(InvalidClientConfigurationException.class);

        DefaultOutboundFallbackRegistry registry = new DefaultOutboundFallbackRegistry(
            List.of(new GenericFallback(), new GenericFallback()));
        assertThatThrownBy(() -> registry.find(
            context("client", "op", String.class), String.class))
            .isInstanceOf(InvalidClientConfigurationException.class);
    }

    private static FallbackContext context(
        String client, String operation, Class<?> type
    ) {
        return FallbackContext.builder().clientName(client).protocol(ExchangeProtocol.HTTP)
            .operation(operation).responseType(type).build();
    }

    @ExchangeFallback(client = "payment-rest", operation = "POST /payments")
    static final class PaymentFallback implements OutboundFallbackHandler<String> {
        @Override public Class<String> responseType() { return String.class; }
        @Override public boolean supports(FallbackContext context) { return true; }
        @Override public String fallback(FallbackContext context) { return "pending"; }
    }

    static final class GenericFallback implements OutboundFallbackHandler<String> {
        @Override public Class<String> responseType() { return String.class; }
        @Override public boolean supports(FallbackContext context) { return true; }
        @Override public String fallback(FallbackContext context) { return "generic"; }
    }
}
