package com.company.platform.core.exception.handler.helper;

import com.company.platform.core.config.jackson.StrictBooleanDeserializer;
import com.company.platform.core.config.jackson.StrictInstantDeserializer;
import com.company.platform.core.config.jackson.StrictLocalDateDeserializer;
import com.company.platform.core.config.jackson.StrictLocalDateTimeDeserializer;
import com.company.platform.core.config.jackson.StrictOffsetDateTimeDeserializer;
import com.company.platform.core.config.jackson.StrictStringDeserializer;
import com.company.platform.core.config.jackson.StrictUuidDeserializer;
import com.company.platform.core.i18n.DefaultI18nService;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.rest.response.ErrorDetail;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JsonExceptionHelperTest {

    private final JsonExceptionHelper helper = new JsonExceptionHelper(i18n());
    private final JsonMapper mapper = strictMapper(true, true);

    @BeforeEach
    void useEnglishMessages() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void mapsNumericBooleanAndTypeFailuresToExactNestedFields() {
        assertDetail(
            failure("{\"child\":{\"count\":\"NaN\"}}", RequestBody.class),
            "child.count",
            "error.validation.field-integer",
            "Field child.count must be an integer."
        );
        assertDetail(
            failure("{\"amount\":\"NaN\"}", TypedBody.class),
            "amount",
            "error.validation.field-number",
            "Field amount must be a number."
        );
        assertDetail(
            failure("{\"active\":\"true\"}", TypedBody.class),
            "active",
            "error.validation.field-boolean",
            "Field active must be true or false."
        );
        assertDetail(
            failure("{\"children\":[{\"count\":{}}]}", RequestList.class),
            "children[0].count",
            "error.validation.field-integer",
            "Field children[0].count must be an integer."
        );
        assertDetail(
            failure("{\"text\":12}", TypedBody.class),
            "text",
            "error.validation.field-type",
            "Field text must have type String."
        );
        assertDetail(
            failure("{\"child\":\"invalid\"}", RequestBody.class),
            "child",
            "error.validation.field-type",
            "Field child must have type Child."
        );
    }

    @Test
    void mapsDateUuidEnumAndConfiguredStringFormatFailures() {
        assertDetail(
            failure("{\"date\":\"29/07/2026\"}", TypedBody.class),
            "date",
            "error.validation.field-date",
            "Field date must be a valid date."
        );
        assertDetail(
            failure("{\"dateTime\":\"29/07/2026 10:00\"}", TypedBody.class),
            "dateTime",
            "error.validation.field-date-time",
            "Field dateTime must be a valid date and time."
        );
        assertDetail(
            failure("{\"offsetDateTime\":\"2026-07-29T10:00:00\"}", TypedBody.class),
            "offsetDateTime",
            "error.validation.field-date-time",
            "Field offsetDateTime must be a valid date and time."
        );
        assertDetail(
            failure("{\"instant\":\"2026-07-29 03:00:00\"}", TypedBody.class),
            "instant",
            "error.validation.field-date-time",
            "Field instant must be a valid date and time."
        );
        assertDetail(
            failure("{\"id\":\"invalid\"}", TypedBody.class),
            "id",
            "error.validation.field-uuid",
            "Field id must be a valid UUID."
        );
        assertDetail(
            failure("{\"mode\":\"UNKNOWN\"}", TypedBody.class),
            "mode",
            "error.validation.field-enum",
            "Field mode must contain one of the supported values: FAST, SAFE."
        );

        JsonMapper restrictedStrings = strictMapper(false, false);
        assertDetail(
            failure(restrictedStrings, "{\"text\":\"Hải\"}", TypedBody.class),
            "text",
            "error.validation.field-format",
            "Field text has an incorrect format."
        );
        assertDetail(
            failure(restrictedStrings, "{\"text\":\"Ada!\"}", TypedBody.class),
            "text",
            "error.validation.field-format",
            "Field text has an incorrect format."
        );
    }

    @Test
    void mapsUnknownNullMalformedAndInvalidDefinitionWithoutLeakingInput() {
        ErrorDetail rootUnknown = detail(failure(
            "{\"unsupported\":\"secret\"}", TypedBody.class));
        assertThat(rootUnknown.getField()).isEqualTo("unsupported");
        assertThat(rootUnknown.getCode()).isEqualTo("error.validation.field-unknown");
        assertThat(rootUnknown.getMessage()).isEqualTo("Field unsupported is not supported.");
        assertThat(rootUnknown.getRejectedValue()).isNull();

        ErrorDetail nestedUnknown = detail(failure(
            "{\"child\":{\"unknown\":\"secret\"}}", RequestBody.class));
        assertThat(nestedUnknown.getField()).isEqualTo("child.unknown");
        assertThat(nestedUnknown.getMessage())
            .isEqualTo("Field child.unknown is not supported.");

        assertDetail(
            failure("{\"requiredText\":null}", TypedBody.class),
            "requiredText",
            "error.validation.field-required",
            "Field requiredText is required."
        );
        assertDetail(
            failure("{\"child\":", RequestBody.class),
            "requestBody",
            "error.validation.json-malformed",
            "Request body is missing or contains malformed JSON."
        );
        assertDetail(
            failure("{}", UnsupportedBody.class),
            "requestBody",
            "error.validation.request-definition-invalid",
            "Request body cannot be converted to the expected type UnsupportedBody."
        );

        ErrorDetail fallback = detail(new IllegalArgumentException(
            "password=must-not-leak"));
        assertThat(fallback.getField()).isEqualTo("requestBody");
        assertThat(fallback.getCode()).isEqualTo("error.validation.json-malformed");
        assertThat(fallback.getMessage())
            .isEqualTo("Request body is missing or contains malformed JSON.");
        assertThat(fallback.toString()).doesNotContain("must-not-leak");
    }

    @Test
    void localizesEveryJacksonFailureMessageToVietnamese() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("vi"));

        assertDetail(
            failure("{\"active\":1}", TypedBody.class),
            "active",
            "error.validation.field-boolean",
            "Trường active chỉ chấp nhận giá trị true hoặc false."
        );
        assertDetail(
            failure("{\"unknown\":1}", TypedBody.class),
            "unknown",
            "error.validation.field-unknown",
            "Trường unknown không được hỗ trợ."
        );
        assertDetail(
            failure("{\"date\":\"invalid\"}", TypedBody.class),
            "date",
            "error.validation.field-date",
            "Trường date phải là ngày hợp lệ."
        );
    }

    private void assertDetail(
        Throwable throwable,
        String field,
        String code,
        String message
    ) {
        ErrorDetail detail = detail(throwable);
        assertThat(detail.getField()).isEqualTo(field);
        assertThat(detail.getCode()).isEqualTo(code);
        assertThat(detail.getMessage()).isEqualTo(message);
        assertThat(detail.getRejectedValue()).isNull();
        assertThat(detail.getMetadata()).isEmpty();
    }

    private ErrorDetail detail(Throwable throwable) {
        return helper.extractJsonErrorDetails(throwable).getFirst();
    }

    private Throwable failure(String json, Class<?> type) {
        return failure(mapper, json, type);
    }

    private static Throwable failure(
        JsonMapper jsonMapper,
        String json,
        Class<?> type
    ) {
        try {
            jsonMapper.readValue(json, type);
            throw new AssertionError("Expected JSON mapping to fail");
        } catch (AssertionError error) {
            throw error;
        } catch (Exception exception) {
            return new RuntimeException(
                "HTTP conversion failed",
                new RuntimeException("adapter failed", exception)
            );
        }
    }

    private static JsonMapper strictMapper(
        boolean allowUnicode,
        boolean allowSpecialCharacters
    ) {
        SimpleModule module = new SimpleModule("handler-test");
        module.addDeserializer(
            String.class,
            new StrictStringDeserializer(
                true, allowUnicode, allowSpecialCharacters)
        );
        module.addDeserializer(Boolean.class, new StrictBooleanDeserializer());
        module.addDeserializer(boolean.class, new StrictBooleanDeserializer());
        module.addDeserializer(LocalDate.class, new StrictLocalDateDeserializer());
        module.addDeserializer(
            LocalDateTime.class, new StrictLocalDateTimeDeserializer());
        module.addDeserializer(
            OffsetDateTime.class, new StrictOffsetDateTimeDeserializer());
        module.addDeserializer(Instant.class, new StrictInstantDeserializer());
        module.addDeserializer(UUID.class, new StrictUuidDeserializer());
        return JsonMapper.builder()
            .addModule(module)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    }

    private static I18nService i18n() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("core_message");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.ENGLISH);
        source.setFallbackToSystemLocale(false);
        return new DefaultI18nService(source);
    }

    public static final class RequestBody {
        private Child child;
        public Child getChild() { return child; }
        public void setChild(Child child) { this.child = child; }
    }

    public static final class RequestList {
        private List<Child> children;
        public List<Child> getChildren() { return children; }
        public void setChildren(List<Child> children) { this.children = children; }
    }

    public static final class Child {
        private int count;
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    public static final class TypedBody {
        private boolean active;
        private BigDecimal amount;
        private String text;
        private String requiredText;
        private LocalDate date;
        private LocalDateTime dateTime;
        private OffsetDateTime offsetDateTime;
        private Instant instant;
        private UUID id;
        private Mode mode;
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getRequiredText() { return requiredText; }
        @JsonSetter(nulls = Nulls.FAIL)
        public void setRequiredText(String requiredText) {
            this.requiredText = requiredText;
        }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public LocalDateTime getDateTime() { return dateTime; }
        public void setDateTime(LocalDateTime dateTime) {
            this.dateTime = dateTime;
        }
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

    public interface UnsupportedBody {
    }

    enum Mode {
        FAST,
        SAFE
    }
}
