package com.company.platform.core.exception.code;

import com.company.platform.core.i18n.I18nKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionCodeContractTest {

    @Test
    void everyExceptionCodeProvidesAStableNonBlankI18nKey() {
        List<I18nKey[]> codeGroups = List.of(
            AuthCode.values(),
            CommonCode.values(),
            ExchangeCode.values(),
            ExchangeCode.CacheCode.values(),
            ExchangeCode.ConcurrentCode.values(),
            ExchangeCode.ConfigurationCode.values(),
            ExchangeCode.DatabaseCode.values(),
            FileCode.values(),
            IdempotencyCode.values(),
            LegacyCode.values(),
            QueueCode.values(),
            RateLimitCode.values(),
            SecurityCode.values(),
            SystemCode.values(),
            TransactionCode.values(),
            ValidationCode.values()
        );

        assertThat(codeGroups).allSatisfy(group ->
            assertThat(group)
                .isNotEmpty()
                .allSatisfy(code -> assertThat(code.getKey()).isNotBlank())
        );
    }
}
