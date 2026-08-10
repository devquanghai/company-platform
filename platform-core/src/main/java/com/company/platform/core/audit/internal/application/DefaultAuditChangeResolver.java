package com.company.platform.core.audit.internal.application;

import com.company.platform.core.audit.AuditChangeResolver;
import com.company.platform.core.audit.AuditChangeSource;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/** Collects changes only from values that explicitly implement {@link AuditChangeSource}. */
public final class DefaultAuditChangeResolver implements AuditChangeResolver {

    @Override
    public Map<String, Object> resolve(Method method, Object[] arguments, Object result) {
        Map<String, Object> changes = new LinkedHashMap<>();
        if (arguments != null) {
            for (Object argument : arguments) {
                collect(argument, changes);
            }
        }
        collect(result, changes);
        return Map.copyOf(changes);
    }

    private static void collect(Object candidate, Map<String, Object> changes) {
        if (candidate instanceof AuditChangeSource source) {
            Map<String, Object> sourceChanges = source.auditChanges();
            if (sourceChanges == null) {
                return;
            }
            sourceChanges.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    changes.put(key, value);
                }
            });
        }
    }
}
