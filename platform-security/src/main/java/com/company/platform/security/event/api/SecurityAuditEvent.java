package com.company.platform.security.event.api;

/** Credential-free security event suitable for an application audit adapter. */
public record SecurityAuditEvent(
    String category,
    String outcome,
    String principal,
    String authenticationType,
    String failureCategory
) {
}
