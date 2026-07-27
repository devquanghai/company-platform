package com.company.platform.core.audit;

import java.util.Map;

/** Explicitly supplies non-sensitive before/after fields for an audited operation. */
public interface AuditChangeSource {
    Map<String, Object> auditChanges();
}
