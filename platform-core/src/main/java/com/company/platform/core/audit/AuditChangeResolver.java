package com.company.platform.core.audit;

import java.lang.reflect.Method;
import java.util.Map;

/** Extracts an application-approved change set without serializing arbitrary arguments. */
public interface AuditChangeResolver {
    Map<String, Object> resolve(Method method, Object[] arguments, Object result);
}
