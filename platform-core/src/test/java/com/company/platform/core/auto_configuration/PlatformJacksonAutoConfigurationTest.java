package com.company.platform.core.auto_configuration;

import com.company.platform.core.configuration.properties.PlatformJacksonProperties;
import com.company.platform.core.auto_configuration.PlatformJacksonAutoConfiguration;
import com.company.platform.core.json.JsonMapperHelper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformJacksonAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(PlatformJacksonAutoConfiguration.class));

    @Test
    void appliesStrictJacksonDefaultsAndTrimsStrings() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(JsonMapperBuilderCustomizer.class);
            JsonMapper mapper = mapper(context.getBean(JsonMapperBuilderCustomizer.class));
            Payload payload = mapper.readValue(
                "{\"name\":\"  Ada  \",\"active\":true,\"count\":12,"
                    + "\"date\":\"2026-07-22\",\"dateTime\":\"2026-07-22T10:11:12\","
                    + "\"offsetDateTime\":\"2026-07-22T10:11:12+07:00\","
                    + "\"instant\":\"2026-07-22T03:11:12Z\","
                    + "\"id\":\"123e4567-e89b-12d3-a456-426614174000\","
                    + "\"mode\":\"FAST\"}",
                Payload.class
            );

            assertThat(payload.getName()).isEqualTo("Ada");
            assertThat(payload.isActive()).isTrue();
            assertThat(payload.getCount()).isEqualTo(12);
            assertThat(payload.getDate()).isEqualTo(LocalDate.of(2026, 7, 22));
            assertThat(payload.getDateTime())
                .isEqualTo(LocalDateTime.of(2026, 7, 22, 10, 11, 12));
            assertThat(payload.getOffsetDateTime())
                .isEqualTo(OffsetDateTime.parse("2026-07-22T10:11:12+07:00"));
            assertThat(payload.getInstant()).isEqualTo(Instant.parse("2026-07-22T03:11:12Z"));
            assertThat(payload.getId())
                .isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
            assertThat(payload.getMode()).isEqualTo(Mode.FAST);
            assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
            assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)).isTrue();
            assertThat(mapper.isEnabled(DeserializationFeature.ACCEPT_FLOAT_AS_INT)).isFalse();
            assertThat(mapper.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)).isFalse();
            assertThat(mapper.isEnabled(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)).isFalse();
            assertThatThrownBy(() -> mapper.readValue("{\"unknown\":1}", Payload.class))
                .isInstanceOf(Exception.class);
            assertThat(mapper.readValue("{\"name\":\"Ada Lovelace\"}", Payload.class).getName())
                .isEqualTo("Ada Lovelace");
            assertThat(mapper.readValue("{\"name\":\"Hải!\"}", Payload.class).getName())
                .isEqualTo("Hải!");
            assertThat(mapper.readValue("{\"active\":false}", Payload.class).isActive()).isFalse();
        });
    }

    @Test
    void supportsExplicitLenientConfigurationAndCanBeDisabled() {
        runner.withPropertyValues(
                "platform.core.jackson.trim-strings=false",
                "platform.core.jackson.fail-on-unknown-properties=false",
                "platform.core.jackson.fail-on-trailing-tokens=false",
                "platform.core.jackson.fail-on-float-to-integer=false",
                "platform.core.jackson.fail-on-null-for-primitives=false",
                "platform.core.jackson.strict-scalar-coercion=false",
                "platform.core.jackson.accept-case-insensitive-enums=true",
                "platform.core.jackson.order-map-entries-by-keys=true",
                "platform.core.jackson.allow-unicode=false",
                "platform.core.jackson.allow-special-characters=false")
            .run(context -> {
                PlatformJacksonProperties properties = context.getBean(PlatformJacksonProperties.class);
                JsonMapper mapper = mapper(context.getBean(JsonMapperBuilderCustomizer.class));
                assertThat(mapper.readValue("{\"name\":\"  Ada  \"}", Payload.class).getName())
                    .isEqualTo("  Ada  ");
                assertThat(properties.isEnabled()).isTrue();
                properties.setEnabled(false);
                properties.setTrimStrings(true);
                properties.setFailOnUnknownProperties(true);
                properties.setFailOnTrailingTokens(true);
                properties.setFailOnFloatToInteger(true);
                properties.setFailOnNullForPrimitives(true);
                properties.setStrictScalarCoercion(true);
                properties.setAcceptCaseInsensitiveEnums(false);
                properties.setOrderMapEntriesByKeys(false);
                assertInvalid(mapper, "{\"name\":\"Hải\"}");
                assertInvalid(mapper, "{\"name\":\"Ada!\"}");
                assertThat(properties.isEnabled()).isFalse();
                assertThat(properties.isFailOnNullForPrimitives()).isTrue();
                assertThat(properties.isStrictScalarCoercion()).isTrue();
            });

        runner.withPropertyValues("platform.core.jackson.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(JsonMapperBuilderCustomizer.class));

        runner.withPropertyValues(
                "platform.core.jackson.allow-unicode=true",
                "platform.core.jackson.allow-special-characters=true")
            .run(context -> {
                JsonMapper mapper = mapper(context.getBean(JsonMapperBuilderCustomizer.class));
                assertThat(mapper.readValue("{\"name\":\"  Hải!  \"}", Payload.class).getName())
                    .isEqualTo("Hải!");
            });
    }

    @Test
    void rejectsCrossTypeScalarsAndInvalidIsoDates() {
        runner.run(context -> {
            JsonMapper mapper = mapper(context.getBean(JsonMapperBuilderCustomizer.class));
            assertInvalid(mapper, "{\"active\":\"true\"}");
            assertInvalid(mapper, "{\"active\":1}");
            assertInvalid(mapper, "{\"count\":\"12\"}");
            assertInvalid(mapper, "{\"count\":1.2}");
            assertInvalid(mapper, "{\"count\":null}");
            assertInvalid(mapper, "{\"name\":12}");
            assertInvalid(mapper, "{\"date\":20260722}");
            assertThat(mapper.readValue("{\"date\":\"\"}", Payload.class).getDate()).isNull();
            assertInvalid(mapper, "{\"date\":\"22/07/2026\"}");
            assertInvalid(mapper, "{\"dateTime\":\"22/07/2026 10:00\"}");
            assertInvalid(mapper, "{\"offsetDateTime\":\"2026-07-22T10:00:00\"}");
            assertInvalid(mapper, "{\"instant\":\"2026-07-22 03:00:00\"}");
            assertInvalid(mapper, "{\"id\":\"not-a-uuid\"}");
            assertInvalid(mapper, "{\"mode\":0}");
        });
    }

    @Test
    void registersJsonMapperHelperAndLetsApplicationsOverrideIt() {
        JsonMapper applicationMapper = JsonMapper.builder().build();
        runner.withBean(JsonMapper.class, () -> applicationMapper)
            .run(context -> {
                assertThat(context).hasSingleBean(JsonMapperHelper.class);
                assertThat(context.getBean(JsonMapperHelper.class).getJsonMapper())
                    .isSameAs(applicationMapper);
            });

        JsonMapperHelper applicationHelper = new JsonMapperHelper(applicationMapper);
        runner.withBean(JsonMapper.class, () -> applicationMapper)
            .withBean(JsonMapperHelper.class, () -> applicationHelper)
            .run(context -> assertThat(context.getBean(JsonMapperHelper.class))
                .isSameAs(applicationHelper));
    }

    private static void assertInvalid(JsonMapper mapper, String json) {
        assertThatThrownBy(() -> mapper.readValue(json, Payload.class))
            .isInstanceOf(Exception.class);
    }

    private static JsonMapper mapper(JsonMapperBuilderCustomizer customizer) {
        JsonMapper.Builder builder = JsonMapper.builder();
        customizer.customize(builder);
        return builder.build();
    }

    public static final class Payload {
        private String name;
        private boolean active;
        private int count;
        private LocalDate date;
        private LocalDateTime dateTime;
        private OffsetDateTime offsetDateTime;
        private Instant instant;
        private UUID id;
        private Mode mode;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public LocalDateTime getDateTime() { return dateTime; }
        public void setDateTime(LocalDateTime dateTime) { this.dateTime = dateTime; }
        public OffsetDateTime getOffsetDateTime() { return offsetDateTime; }
        public void setOffsetDateTime(OffsetDateTime offsetDateTime) {
            this.offsetDateTime = offsetDateTime;
        }
        public Instant getInstant() { return instant; }
        public void setInstant(Instant instant) { this.instant = instant; }
        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public Mode getMode() { return mode; }
        public void setMode(Mode mode) { this.mode = mode; }
    }

    enum Mode { FAST, SAFE }
}
