package com.company.platform.core.i18n;

public interface I18nService {

    String get(String key);

    String get(I18nKey errorCode);

    String get(I18nKey errorCode, Object... objects);

    String get(String key, Object... objects);

    String getOrDefault(String key, String defaultMessage, Object... objects);
}
