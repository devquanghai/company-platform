package com.company.platform.core.auto_configuration;

import com.company.platform.core.json.internal.adapter.jackson.*;
import com.company.platform.core.configuration.properties.PlatformJacksonProperties;
import com.company.platform.core.json.JsonMapperHelper;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.LogicalType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.UnaryOperator;

@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnClass(JsonMapper.class)
@ConditionalOnProperty(prefix = "platform.core.jackson", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(PlatformJacksonProperties.class)
public class PlatformJacksonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JsonMapper.class)
    JsonMapperHelper jsonMapperHelper(JsonMapper mapper) {
        return new JsonMapperHelper(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(name = "platformJacksonCustomizer")
    JsonMapperBuilderCustomizer platformJacksonCustomizer(PlatformJacksonProperties properties) {
        return builder -> customize(builder, properties);
    }

    public static JsonMapper customize(JsonMapper.Builder builder, PlatformJacksonProperties properties) {
        builder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, properties.isFailOnUnknownProperties())
            .configure(DeserializationFeature.FAIL_ON_TRAILING_TOKENS, properties.isFailOnTrailingTokens())
            .configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, !properties.isFailOnFloatToInteger())
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, properties.isFailOnNullForPrimitives())
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, properties.isAcceptCaseInsensitiveEnums())
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, properties.isOrderMapEntriesByKeys());

        if (properties.isStrictScalarCoercion()) {
            configureStrictScalarCoercion(builder);
        }

        SimpleModule module = new SimpleModule("platform-core-validation");
        module.addDeserializer(String.class, new StrictStringDeserializer(
            properties.isTrimStrings(),
            properties.isAllowUnicode(),
            properties.isAllowSpecialCharacters()
        ));
        module.addDeserializer(UUID.class, new StrictUuidDeserializer());
        module.addDeserializer(LocalDate.class, new StrictLocalDateDeserializer());
        module.addDeserializer(LocalDateTime.class, new StrictLocalDateTimeDeserializer());
        module.addDeserializer(OffsetDateTime.class, new StrictOffsetDateTimeDeserializer());
        module.addDeserializer(Instant.class, new StrictInstantDeserializer());
        module.addDeserializer(Boolean.class, new StrictBooleanDeserializer());
        module.addDeserializer(boolean.class, new StrictBooleanDeserializer());

        builder.addModule(module);

        builder.changeDefaultPropertyInclusion(inclusion ->
            JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL)
        );

        return builder.build();
    }


    private static void configureStrictScalarCoercion(JsonMapper.Builder builder) {
        builder.withCoercionConfig(LogicalType.Integer, coercion -> {
            coercion.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
        });
        builder.withCoercionConfig(LogicalType.Float, coercion -> {
            coercion.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
        });
        builder.withCoercionConfig(LogicalType.Boolean, coercion -> {
            coercion.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
        });
        builder.withCoercionConfig(LogicalType.Textual, coercion -> {
            coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
        builder.withCoercionConfig(LogicalType.Enum, coercion -> {
            coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
        });
        builder.withCoercionConfig(LogicalType.DateTime, coercion -> {
            coercion.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
            coercion.setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail);
        });
    }
}
