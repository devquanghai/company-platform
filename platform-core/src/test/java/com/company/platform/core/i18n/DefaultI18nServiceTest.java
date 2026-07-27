package com.company.platform.core.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DefaultI18nServiceTest {

    private final ResourceBundleMessageSource source = source();
    private final I18nService service = new DefaultI18nService(source);

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void resolvesStringAndTypedKeysInCurrentLocale() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(service.get("MSG0000")).isEqualTo("Successfully.");
        assertThat(service.get(() -> "MSG0000")).isEqualTo("Successfully.");

        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));
        assertThat(service.get(() -> "MSG0011", 3, 5))
            .isEqualTo("Tài khoản của bạn đã bị khóa do đăng nhập sai 3 lần. Vui lòng thử lại sau 5 phút.");
    }

    @Test
    void treatsNullArgumentsAsAnEmptyArrayAndRejectsNullKeys() {
        assertThat(service.get("missing-key", (Object[]) null)).isEqualTo("missing-key");
        assertThat(service.getOrDefault("missing-key", "Fallback {0}", "message"))
            .isEqualTo("Fallback message");
        ResourceBundleMessageSource strictSource = source();
        strictSource.setUseCodeAsDefaultMessage(false);
        I18nService strictService = new DefaultI18nService(strictSource);
        assertThat(strictService.getOrDefault("missing-key", "Fallback {0}", "message"))
            .isEqualTo("Fallback message");
        assertThat(strictService.getOrDefault("missing-key", "Fallback", (Object[]) null))
            .isEqualTo("Fallback");
        assertThatNullPointerException().isThrownBy(() -> service.get((String) null));
        assertThatNullPointerException().isThrownBy(() -> service.getOrDefault(null, "fallback"));
        assertThatNullPointerException().isThrownBy(() -> service.getOrDefault("key", null));
        assertThatNullPointerException().isThrownBy(() -> service.get((I18nKey) null));
        assertThatNullPointerException().isThrownBy(() -> service.get((I18nKey) null, "argument"));
    }

    @Test
    void requiresMessageSource() {
        assertThatNullPointerException().isThrownBy(() -> new DefaultI18nService(null));
    }

    private static ResourceBundleMessageSource source() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.ENGLISH);
        source.setFallbackToSystemLocale(false);
        source.setUseCodeAsDefaultMessage(true);
        return source;
    }
}
