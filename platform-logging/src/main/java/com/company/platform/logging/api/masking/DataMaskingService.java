package com.company.platform.logging.api.masking;

import com.company.platform.logging.annotation.masking.Sensitive;
import com.company.platform.logging.domain.model.SanitizedThrowable;

import java.util.Map;

public interface DataMaskingService {
    String maskValue(String fieldName, String value);
    Object sanitize(Object source);
    default Object sanitizeAnnotated(Object source, Sensitive annotation) {
        return sanitize(source);
    }
    String sanitizeJson(String json);
    String sanitizeMessage(String message);
    Map<String, Object> sanitizeFields(Map<String, ?> fields);
    SanitizedThrowable sanitizeThrowable(Throwable throwable);
}
