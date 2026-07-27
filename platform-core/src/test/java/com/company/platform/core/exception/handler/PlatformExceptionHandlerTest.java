package com.company.platform.core.exception.handler;

import com.company.platform.core.context.RequestContextProvider;
import com.company.platform.core.configuration.properties.PlatformCoreExceptionProperties;
import com.company.platform.core.exception.PlatformBusinessException;
import com.company.platform.core.exception.code.CommonCode;
import com.company.platform.core.i18n.DefaultI18nService;
import com.company.platform.core.i18n.I18nService;
import com.company.platform.core.rest.factory.ResponseMetadataFactory;
import com.company.platform.core.rest.response.ApiResponse;
import com.company.platform.core.time.SystemTimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PlatformExceptionHandlerTest {

    private final PlatformCoreExceptionProperties properties = new PlatformCoreExceptionProperties();
    private final I18nService i18n = i18n();
    private final PlatformExceptionHandler handler =
        new PlatformExceptionHandler(metadataFactory(), properties, i18n);
    private final MockHttpServletRequest request = request();

    @BeforeEach
    void useEnglishMessages() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void mapsPlatformExceptionToItsCategoryStatusAndMetadata() {
        ResponseEntity<ApiResponse<Void>> response = handler.handlePlatformException(
            new PlatformBusinessException("ORDER.INVALID", "Order is invalid"),
            request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo("ORDER.INVALID");
        assertThat(response.getBody().getMetadata().getUrl()).isEqualTo("/orders");
        assertThat(response.getBody().getMetadata().getMethod()).isEqualTo("POST");
    }

    @Test
    void mapsBindingErrorsAndHidesRejectedValuesByDefault() {
        BindException bindException = new BindException(new Form(), "form");
        bindException.rejectValue("name", "NotBlank", "Name is required");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBindException(bindException, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError().getDetails()).singleElement().satisfies(detail -> {
            assertThat(detail.getField()).isEqualTo("name");
            assertThat(detail.getCode()).isEqualTo("NotBlank");
            assertThat(detail.getRejectedValue()).isNull();
        });
    }

    @Test
    void mapsConstraintAndHandlerMethodValidationErrors() throws Exception {
        ConstraintViolation<Object> violation = constraintViolation(
            "command.name", "must not be blank", "bad");
        ResponseEntity<ApiResponse<Void>> constrained = handler.handleConstraintViolation(
            new ConstraintViolationException(Set.of(violation)), request);
        assertThat(constrained.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(constrained.getBody().getError().getDetails()).singleElement().satisfies(detail -> {
            assertThat(detail.getField()).isEqualTo("command.name");
            assertThat(detail.getRejectedValue()).isNull();
        });

        properties.setIncludeRejectedValue(true);
        ResponseEntity<ApiResponse<Void>> exposedConstraint = handler.handleConstraintViolation(
            new ConstraintViolationException(Set.of(violation)), request);
        assertThat(exposedConstraint.getBody().getError().getDetails().getFirst().getRejectedValue())
            .isEqualTo("bad");

        Method method = Form.class.getDeclaredMethod("validate", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        ParameterValidationResult result = new ParameterValidationResult(
            parameter,
            "bad",
            List.of(
                new DefaultMessageSourceResolvable(
                    new String[]{"NotBlank"}, null, "Required"),
                new DefaultMessageSourceResolvable(new String[0], null, "Invalid"),
                new DefaultMessageSourceResolvable(null, null, null)
            ),
            null,
            null,
            null,
            (error, sourceType) -> null
        );
        HandlerMethodValidationException exception = new HandlerMethodValidationException(
            MethodValidationResult.create(new Form(), method, List.of(result)));
        ResponseEntity<ApiResponse<Void>> methodResponse =
            handler.handleMethodValidation(exception, request);
        assertThat(methodResponse.getBody().getError().getDetails()).hasSize(3);
        assertThat(methodResponse.getBody().getError().getDetails().getFirst().getCode())
            .isEqualTo("NotBlank");
        assertThat(methodResponse.getBody().getError().getDetails().getFirst().getRejectedValue())
            .isEqualTo("bad");
        assertThat(methodResponse.getBody().getError().getDetails().get(1).getCode())
            .isEqualTo("error.validation.field-invalid");

        properties.setIncludeRejectedValue(false);
        MethodParameter unnamed = new MethodParameter(method, 0) {
            @Override public String getParameterName() { return null; }
        };
        ParameterValidationResult unnamedResult = new ParameterValidationResult(
            unnamed,
            "hidden",
            List.of(new DefaultMessageSourceResolvable(
                new String[]{"Invalid"}, null, "Invalid")),
            null, null, null, (error, sourceType) -> null
        );
        HandlerMethodValidationException unnamedException = new HandlerMethodValidationException(
            MethodValidationResult.create(new Form(), method, List.of(unnamedResult)));
        var unnamedResponse = handler.handleMethodValidation(unnamedException, request);
        assertThat(unnamedResponse.getBody().getError().getDetails().getFirst().getField())
            .isEqualTo("argument");
        assertThat(unnamedResponse.getBody().getError().getDetails().getFirst().getRejectedValue())
            .isNull();
    }

    @Test
    void canExposeRejectedValueWhenExplicitlyEnabled() {
        properties.setIncludeRejectedValue(true);
        Form form = new Form();
        form.setName("bad-value");
        BindException bindException = new BindException(form, "form");
        bindException.rejectValue("name", null, null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBindException(bindException, null);

        assertThat(response.getBody().getError().getDetails()).singleElement().satisfies(detail -> {
            assertThat(detail.getCode()).isEqualTo("error.validation.field-invalid");
            assertThat(detail.getMessage()).isEqualTo("Field name is invalid.");
            assertThat(detail.getRejectedValue()).isEqualTo("bad-value");
        });
        assertThat(response.getBody().getMetadata().getUrl()).isNull();

        BindException nullMessages = new BindException(new Form(), "form");
        nullMessages.addError(new FieldError(
            "form", "name", "rejected", false, null, null, null));
        ResponseEntity<ApiResponse<Void>> nullMessageResponse =
            handler.handleBindException(nullMessages, request);
        assertThat(nullMessageResponse.getBody().getError().getDetails()).singleElement().satisfies(detail -> {
            assertThat(detail.getCode()).isEqualTo("error.validation.field-invalid");
            assertThat(detail.getMessage()).isEqualTo("Field name is invalid.");
        });
    }

    @Test
    void mapsMalformedAndUnexpectedExceptionsWithoutLeakingInternals() {
        ResponseEntity<ApiResponse<Void>> invalid = handler.handleInvalidRequest(
            new IllegalArgumentException("sensitive parser detail"), request);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().getError().getCode()).isEqualTo(CommonCode.INVALID_REQUEST.getKey());

        ResponseEntity<ApiResponse<Void>> unexpected = handler.handleUnexpectedException(
            new IllegalStateException("database password"), request);
        assertThat(unexpected.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(unexpected.getBody().getError().getMessage())
            .isEqualTo("An unexpected internal server error occurred.");
    }

    @Test
    void mapsStandardSpringMvcExceptionsToStableI18nResponses() {
        assertThat(handler.handleResourceNotFound(
            new NoResourceFoundException(HttpMethod.GET, "/missing", "/missing"), request)
            .getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleMethodNotAllowed(
            new HttpRequestMethodNotSupportedException("TRACE"), request).getStatusCode())
            .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(handler.handleUnsupportedMediaType(
            new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_XML, java.util.List.of()),
            request).getStatusCode())
            .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(handler.handleNotAcceptable(
            new HttpMediaTypeNotAcceptableException("not acceptable"), request).getStatusCode())
            .isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(handler.handleRequestTimeout(
            new AsyncRequestTimeoutException(), request).getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(handler.handleMaxUploadSizeExceeded(
            new MaxUploadSizeExceededException(1024), request).getStatusCode())
            .isEqualTo(HttpStatus.CONTENT_TOO_LARGE);

        ResponseEntity<ApiResponse<Void>> clientError = handler.handleErrorResponse(
            new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS), request);
        assertThat(clientError.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(clientError.getBody().getError().getCategory()).isEqualTo(
            com.company.platform.core.exception.error.ErrorCategory.VALIDATION);

        ResponseEntity<ApiResponse<Void>> serverError = handler.handleErrorResponse(
            new ErrorResponseException(HttpStatus.BAD_GATEWAY), request);
        assertThat(serverError.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(serverError.getBody().getError().getCategory()).isEqualTo(
            com.company.platform.core.exception.error.ErrorCategory.INTERNAL);
    }

    @Test
    void validatesConstructorDependenciesAndProperties() {
        assertThatNullPointerException()
            .isThrownBy(() -> new PlatformExceptionHandler(null, properties, i18n));
        assertThatNullPointerException()
            .isThrownBy(() -> new PlatformExceptionHandler(metadataFactory(), null, i18n));
        assertThatNullPointerException()
            .isThrownBy(() -> new PlatformExceptionHandler(metadataFactory(), properties, null));

        PlatformCoreExceptionProperties configured = new PlatformCoreExceptionProperties();
        assertThat(configured.isEnabled()).isTrue();
        assertThat(configured.isIncludeRejectedValue()).isFalse();
        configured.setEnabled(false);
        configured.setIncludeRejectedValue(true);
        assertThat(configured.isEnabled()).isFalse();
        assertThat(configured.isIncludeRejectedValue()).isTrue();
    }

    private static ResponseMetadataFactory metadataFactory() {
        RequestContextProvider requestContext = new RequestContextProvider() {
            public String getRequestId() { return "request-1"; }
            public String getCorrelationId() { return "correlation-1"; }
        };
        SystemTimeProvider time = new SystemTimeProvider(
            Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneId.of("UTC")),
            ZoneId.of("UTC")
        );
        return new ResponseMetadataFactory(requestContext, CurrentTraceContext::empty, time);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.setRequestURI("/orders");
        return request;
    }

    private static I18nService i18n() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("core_message");
        source.setDefaultEncoding("UTF-8");
        source.setDefaultLocale(Locale.ENGLISH);
        source.setFallbackToSystemLocale(false);
        return new DefaultI18nService(source);
    }

    public static final class Form {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public void validate(String value) { }
    }

    @SuppressWarnings("unchecked")
    private static ConstraintViolation<Object> constraintViolation(
        String path,
        String message,
        Object invalidValue
    ) {
        Path propertyPath = (Path) Proxy.newProxyInstance(
            Path.class.getClassLoader(),
            new Class<?>[]{Path.class},
            (proxy, method, args) -> method.getName().equals("toString") ? path : null
        );
        return (ConstraintViolation<Object>) Proxy.newProxyInstance(
            ConstraintViolation.class.getClassLoader(),
            new Class<?>[]{ConstraintViolation.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getPropertyPath" -> propertyPath;
                case "getMessage" -> message;
                case "getInvalidValue" -> invalidValue;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> null;
            }
        );
    }
}
