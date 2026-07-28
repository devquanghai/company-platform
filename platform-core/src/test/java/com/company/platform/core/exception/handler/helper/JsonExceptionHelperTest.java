package com.company.platform.core.exception.handler.helper;

import com.company.platform.core.i18n.I18nKey;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.rest.response.ErrorDetail;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonExceptionHelperTest {

    private final JsonExceptionHelper helper = new JsonExceptionHelper(new FallbackI18nService());
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void mapsInvalidFormatWithNestedFieldPath() {
        Throwable failure = deserializeFailure(
            "{\"child\":{\"count\":\"not-a-number\"}}",
            RequestBody.class
        );

        ErrorDetail detail = helper.extractJsonErrorDetails(failure).getFirst();

        assertThat(detail.getField()).isEqualTo("child.count");
        assertThat(detail.getCode()).isEqualTo("error.validation.field-invalid");
        assertThat(detail.getMessage()).isEqualTo("Invalid value.");
        assertThat(detail.getRejectedValue()).isNull();
    }

    @Test
    void mapsIndexedPathsGenericJacksonFailuresAndInvalidDefinitions() {
        Throwable indexed = deserializeFailure(
            "{\"children\":[{\"count\":{}}]}",
            RequestList.class
        );
        assertThat(helper.extractJsonErrorDetails(indexed).getFirst().getField())
            .isEqualTo("children[0].count");

        Throwable rootFailure = deserializeFailure("[]", Child.class);
        assertThat(helper.extractJsonErrorDetails(rootFailure).getFirst().getField())
            .isEqualTo("requestBody");

        Throwable definition = deserializeFailure("{}", UnsupportedBody.class);
        ErrorDetail definitionDetail = helper.extractJsonErrorDetails(definition).getFirst();
        assertThat(definitionDetail.getField()).isEqualTo("requestBody");
        assertThat(definitionDetail.getMessage())
            .isEqualTo("Request object definition is invalid.");
    }

    @Test
    void returnsSafeFallbackWhenNoJacksonCauseExists() {
        List<ErrorDetail> details = helper.extractJsonErrorDetails(
            new IllegalArgumentException("sensitive internal detail"));

        assertThat(details).singleElement().satisfies(detail -> {
            assertThat(detail.getField()).isEqualTo("requestBody");
            assertThat(detail.getMessage()).isEqualTo("Invalid request body.");
        });
    }

    private Throwable deserializeFailure(String json, Class<?> type) {
        try {
            mapper.readValue(json, type);
            throw new AssertionError("Expected JSON mapping to fail");
        } catch (AssertionError error) {
            throw error;
        } catch (Exception exception) {
            return new RuntimeException("HTTP conversion failed", exception);
        }
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

    public interface UnsupportedBody {
    }

    private static final class FallbackI18nService implements I18nService {
        @Override public String get(String key) { return key; }
        @Override public String get(I18nKey errorCode) { return errorCode.getKey(); }
        @Override public String get(I18nKey errorCode, Object... objects) {
            return errorCode.getKey();
        }
        @Override public String get(String key, Object... objects) { return key; }
        @Override public String getOrDefault(
            String key, String defaultMessage, Object... objects
        ) {
            return defaultMessage;
        }
    }
}
