package com.company.platform.core.rest.factory;

import com.company.platform.core.context.MdcRequestContextProvider;
import com.company.platform.core.context.CurrentUser;
import com.company.platform.core.exception.error.ErrorCategory;
import com.company.platform.core.rest.response.ApiError;
import com.company.platform.core.time.SystemTimeProvider;
import com.company.platform.core.trace.CurrentTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ApiResponseFactoryTest {

    @AfterEach
    void clearContext() {
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void createsSuccessAndFailureWithCompleteCurrentRequestMetadata() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put("requestId", "request-1");
        MDC.put("correlationId", "correlation-1");
        MdcRequestContextProvider provider = new MdcRequestContextProvider();
        ResponseMetadataFactory metadata = new ResponseMetadataFactory(
            provider,
            () -> new CurrentTraceContext("trace-1", "span-1"),
            new SystemTimeProvider()
        );
        ApiResponseFactory factory = new ApiResponseFactory(metadata);

        var success = factory.success("created", Map.of("tenant", "company"));
        assertThat(success.isSuccess()).isTrue();
        assertThat(success.getMetadata().getUrl()).isEqualTo("/orders");
        assertThat(success.getMetadata().getMethod()).isEqualTo("POST");
        assertThat(success.getMetadata().getRequestId()).isEqualTo("request-1");
        assertThat(success.getMetadata().getCorrelationId()).isEqualTo("correlation-1");
        assertThat(success.getMetadata().getTraceId()).isEqualTo("trace-1");
        assertThat(success.getMetadata().getSpanId()).isEqualTo("span-1");
        assertThat(success.getMetadata().getTimestamp()).isNotNull();
        assertThat(success.getMetadata().getAttributes()).containsEntry("tenant", "company");
        assertThat(factory.success().isSuccess()).isTrue();
        assertThat(factory.success("ok").getData()).isEqualTo("ok");

        var failure = factory.failure(ApiError.of(
            "FAILED", "Failed", ErrorCategory.INTERNAL));
        assertThat(failure.isSuccess()).isFalse();
        assertThat(failure.getMetadata().getUrl()).isEqualTo("/orders");
    }

    @Test
    void providerReturnsNullOutsideARequestAndFactoryValidatesDependency() {
        MdcRequestContextProvider provider = new MdcRequestContextProvider();
        assertThat(provider.getRequestId()).isNull();
        assertThat(provider.getCorrelationId()).isNull();
        MDC.put("requestId", " ");
        MDC.put("correlationId", "correlation");
        assertThat(provider.getRequestId()).isNull();
        assertThat(provider.getCorrelationId()).isEqualTo("correlation");
        assertThat(provider.getRequestUrl()).isNull();
        assertThat(provider.getRequestMethod()).isNull();
        assertThatNullPointerException().isThrownBy(() -> new ApiResponseFactory(null));

        CurrentUser user = new CurrentUser();
        user.setUserId("1");
        user.setUsername("ada");
        user.setRoles(java.util.Set.of("ADMIN"));
        user.setPermissions(java.util.Set.of("order:update"));
        user.setAttributes(Map.of("tenant", "company"));
        user.setMetaData(Map.of("source", "test"));
        assertThat(user.getUserId()).isEqualTo("1");
        assertThat(user.getUsername()).isEqualTo("ada");
        assertThat(user.getRoles()).contains("ADMIN");
        assertThat(user.getPermissions()).contains("order:update");
        assertThat(user.getAttributes()).containsEntry("tenant", "company");
        assertThat(user.getMetaData()).containsEntry("source", "test");
    }
}
