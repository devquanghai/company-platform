package com.company.platform.core.i18n;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Objects;

public final class DefaultI18nService implements I18nService {

    private final MessageSource messageSource;

    public DefaultI18nService(MessageSource messageSource) {
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource must not be null");
    }

    @Override
    public String get(String key) {
        return get(key, new Object[0]);
    }

    @Override
    public String get(I18nKey errorCode) {
        return get(requireKey(errorCode));
    }

    @Override
    public String get(I18nKey errorCode, Object... objects) {
        return get(requireKey(errorCode), objects);
    }

    @Override
    public String get(String key, Object... objects) {
        String requiredKey = Objects.requireNonNull(key, "key must not be null");
        Object[] arguments = objects == null ? new Object[0] : objects;
        return messageSource.getMessage(requiredKey, arguments, LocaleContextHolder.getLocale());
    }

    @Override
    public String getOrDefault(String key, String defaultMessage, Object... objects) {
        String requiredKey = Objects.requireNonNull(key, "key must not be null");
        String requiredDefault = Objects.requireNonNull(defaultMessage, "defaultMessage must not be null");
        Object[] arguments = objects == null ? new Object[0] : objects;
        return messageSource.getMessage(
            requiredKey,
            arguments,
            requiredDefault,
            LocaleContextHolder.getLocale()
        );
    }

    private static String requireKey(I18nKey key) {
        return Objects.requireNonNull(key, "errorCode must not be null").getKey();
    }
}
