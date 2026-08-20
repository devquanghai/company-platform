package com.company.platform.security.event.internal;

import com.company.platform.security.event.api.SecurityAuditEvent;
import com.company.platform.security.event.api.SecurityAuditEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;

public final class SpringSecurityEventAdapter {
    private final SecurityAuditEventPublisher publisher;

    public SpringSecurityEventAdapter(SecurityAuditEventPublisher publisher) {
        this.publisher = publisher;
    }

    @EventListener
    public void authenticationSucceeded(AuthenticationSuccessEvent event) {
        publish("authentication", "success", event.getAuthentication().getClass().getSimpleName(), null);
    }

    @EventListener
    public void authenticationFailed(AbstractAuthenticationFailureEvent event) {
        publish("authentication", "failure", event.getAuthentication().getClass().getSimpleName(),
            event.getException().getClass().getSimpleName());
    }

    @EventListener
    public void authorizationDenied(AuthorizationDeniedEvent<?> event) {
        var authentication = event.getAuthentication().get();
        String type = authentication == null ? "unknown" : authentication.getClass().getSimpleName();
        publish("authorization", "denied", type, "AccessDenied");
    }

    private void publish(String category, String outcome, String authenticationType, String failureCategory) {
        publisher.publish(new SecurityAuditEvent(category, outcome, null, authenticationType, failureCategory));
    }
}
