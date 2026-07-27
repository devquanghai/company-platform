package com.company.platform.core.web;

import com.company.platform.core.configuration.properties.PlatformWebProperties;
import com.company.platform.core.context.MdcRequestContextProvider;
import com.company.platform.core.trace.TraceHeaders;
import com.company.platform.core.web.filter.RequestResponseLoggingFilter;
import com.company.platform.core.web.filter.TraceContextFilter;
import com.company.platform.core.web.interceptor.RequestTimingInterceptor;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformWebInfrastructureTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void traceFilterAcceptsSafeIdsGeneratesUnsafeIdsAndRestoresMdc() throws Exception {
        TraceContextFilter filter = new TraceContextFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.addHeader(TraceHeaders.REQUEST_ID, "request-1");
        request.addHeader(TraceHeaders.CORRELATION_ID, "correlation-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put("existing", "value");
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeader(TraceHeaders.REQUEST_ID)).isEqualTo("request-1");
        assertThat(response.getHeader(TraceHeaders.CORRELATION_ID)).isEqualTo("correlation-1");
        assertThat(MDC.get("existing")).isEqualTo("value");

        MDC.clear();
        MockHttpServletRequest unsafe = new MockHttpServletRequest("GET", "/unsafe");
        unsafe.addHeader(TraceHeaders.REQUEST_ID, "bad\nidentifier");
        unsafe.addHeader(TraceHeaders.CORRELATION_ID, " ");
        MockHttpServletResponse generated = new MockHttpServletResponse();
        filter.doFilter(unsafe, generated, new MockFilterChain());
        assertThat(generated.getHeader(TraceHeaders.REQUEST_ID)).matches("[0-9a-f-]{36}");
        assertThat(generated.getHeader(TraceHeaders.CORRELATION_ID))
            .isEqualTo(generated.getHeader(TraceHeaders.REQUEST_ID));
        assertThat(MDC.getCopyOfContextMap()).isNull();

        MockHttpServletRequest oversized = new MockHttpServletRequest("GET", "/oversized");
        oversized.addHeader(TraceHeaders.REQUEST_ID, "x".repeat(129));
        MockHttpServletResponse oversizedResponse = new MockHttpServletResponse();
        filter.doFilter(oversized, oversizedResponse, new MockFilterChain());
        assertThat(oversizedResponse.getHeader(TraceHeaders.REQUEST_ID))
            .matches("[0-9a-f-]{36}");
    }

    @Test
    void requestResponseFilterSupportsSummaryPayloadBoundsAndBinaryContent() throws Exception {
        PlatformWebProperties properties = new PlatformWebProperties();
        RequestResponseLoggingFilter filter = new RequestResponseLoggingFilter(properties);
        MockHttpServletRequest summaryRequest = new MockHttpServletRequest("GET", "/summary");
        filter.doFilter(summaryRequest, new MockHttpServletResponse(), new MockFilterChain());

        properties.setIncludePayload(true);
        properties.setMaxPayloadLength(4);
        MockHttpServletRequest payloadRequest = new MockHttpServletRequest("POST", "/payload");
        payloadRequest.setContentType("application/json");
        payloadRequest.setContent("{\n\"name\":\"Ada\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse payloadResponse = new MockHttpServletResponse();
        filter.doFilter(payloadRequest, payloadResponse, echoChain("application/json", "{\"ok\":true}"));
        assertThat(payloadResponse.getContentAsString()).isEqualTo("{\"ok\":true}");

        properties.setMaxPayloadLength(-1);
        MockHttpServletRequest binaryRequest = new MockHttpServletRequest("POST", "/binary");
        binaryRequest.setContentType("application/octet-stream");
        binaryRequest.setContent(new byte[]{1, 2});
        filter.doFilter(
            binaryRequest,
            new MockHttpServletResponse(),
            echoChain("application/octet-stream", "binary")
        );

        exercisePayloadType(filter, null, "value");
        exercisePayloadType(filter, "application/json", "");
        exercisePayloadType(filter, "text/plain", "plain");
        exercisePayloadType(filter, "application/xml", "<value/>");
        exercisePayloadType(filter, "application/x-www-form-urlencoded", "name=Ada");
    }

    @Test
    void timingInterceptorAddsDeterministicServerTimingHeader() throws Exception {
        AtomicLong clock = new AtomicLong(1_000_000L);
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor(clock::get);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        clock.set(3_500_000L);
        interceptor.afterCompletion(request, response, new Object(), null);
        assertThat(response.getHeader("Server-Timing")).isEqualTo("app;dur=2.50");

        MockHttpServletResponse untouched = new MockHttpServletResponse();
        interceptor.afterCompletion(new MockHttpServletRequest(), untouched, new Object(), null);
        assertThat(untouched.getHeader("Server-Timing")).isNull();
    }

    @Test
    void requestContextProviderExposesSafeDeviceRequestInformation() {
        MdcRequestContextProvider provider = new MdcRequestContextProvider();
        assertThat(provider.getRemoteAddress()).isNull();
        assertThat(provider.getUserAgent()).isNull();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/audit");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(provider.getRemoteAddress()).isEqualTo("127.0.0.1");
        assertThat(provider.getUserAgent()).isEqualTo("JUnit");
    }

    private static MockFilterChain echoChain(String contentType, String body) {
        return new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {
                request.getInputStream().readAllBytes();
                response.setContentType(contentType);
                response.getWriter().write(body);
            }
        });
    }

    private static void exercisePayloadType(
        RequestResponseLoggingFilter filter,
        String contentType,
        String body
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/content-type");
        request.setContentType(contentType);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        filter.doFilter(request, new MockHttpServletResponse(), echoChain(contentType, body));
    }
}
