package com.company.platform.core.web;

import com.company.platform.core.configuration.properties.PlatformWebProperties;
import com.company.platform.core.web.filter.RequestCachingFilter;
import com.company.platform.core.web.filter.RequestResponseLoggingFilter;
import com.company.platform.core.web.wrapper.CachedBodyHttpServletRequestWrapper;
import com.company.platform.core.web.wrapper.RequestBodyCachingLimitExceededException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestCachingInfrastructureTest {

    @Test
    void wrapperProvidesDefensiveRepeatableBlockingAndListenerReads() throws Exception {
        MockHttpServletRequest request = request("payload", "text/plain");
        CachedBodyHttpServletRequestWrapper wrapper =
            new CachedBodyHttpServletRequestWrapper(request, 32);

        assertThat(wrapper.getReader().readLine()).isEqualTo("payload");
        assertThat(wrapper.getInputStream().readAllBytes())
            .isEqualTo("payload".getBytes(StandardCharsets.UTF_8));
        byte[] exposed = wrapper.getCachedBody();
        exposed[0] = 'X';
        assertThat(wrapper.getCachedBody()[0]).isEqualTo((byte) 'p');

        ServletInputStream stream = wrapper.getInputStream();
        assertThat(stream.isReady()).isTrue();
        AtomicBoolean data = new AtomicBoolean();
        AtomicBoolean complete = new AtomicBoolean();
        stream.setReadListener(listener(data, complete, new AtomicBoolean(), false));
        assertThat(data).isTrue();
        stream.readAllBytes();
        stream.setReadListener(listener(data, complete, new AtomicBoolean(), false));
        assertThat(stream.isFinished()).isTrue();
        assertThat(complete).isTrue();

        AtomicBoolean failed = new AtomicBoolean();
        wrapper.getInputStream().setReadListener(listener(
            new AtomicBoolean(), new AtomicBoolean(), failed, true));
        assertThat(failed).isTrue();
        assertThatNullPointerException().isThrownBy(() -> wrapper.getInputStream()
            .setReadListener(null));
    }

    @Test
    void wrapperValidatesConstructionAndBodyLimit() throws IOException {
        assertThatNullPointerException().isThrownBy(() ->
            new CachedBodyHttpServletRequestWrapper(null, 1));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new CachedBodyHttpServletRequestWrapper(request("", null), -1));
        assertThatIllegalArgumentException().isThrownBy(() ->
            new CachedBodyHttpServletRequestWrapper(request("", null), Integer.MAX_VALUE));
        MockHttpServletRequest defaultEncoding = new MockHttpServletRequest();
        defaultEncoding.setContent("body".getBytes(StandardCharsets.UTF_8));
        assertThat(new CachedBodyHttpServletRequestWrapper(defaultEncoding, 16)
            .getReader().readLine()).isEqualTo("body");
        assertThatThrownBy(() ->
            new CachedBodyHttpServletRequestWrapper(request("large", null), 2))
            .isInstanceOf(RequestBodyCachingLimitExceededException.class)
            .satisfies(error -> assertThat(
                ((RequestBodyCachingLimitExceededException) error).getMaximumBodySize())
                .isEqualTo(2));
    }

    @Test
    void cachingFilterWrapsOnceSkipsIneligibleBodiesAndRejectsOversize() throws Exception {
        PlatformWebProperties properties = new PlatformWebProperties();
        properties.setMaxCachedRequestBodySize(16);
        RequestCachingFilter filter = new RequestCachingFilter(properties);
        AtomicBoolean repeated = new AtomicBoolean();
        filter.doFilter(request("payload", "application/json"), new MockHttpServletResponse(),
            (request, response) -> {
                var wrapper = (CachedBodyHttpServletRequestWrapper) request;
                repeated.set(wrapper.getInputStream().readAllBytes().length == 7
                    && wrapper.getInputStream().readAllBytes().length == 7);
            });
        assertThat(repeated).isTrue();

        MockHttpServletRequest empty = new MockHttpServletRequest("GET", "/empty");
        empty.setContent(new byte[0]);
        filter.doFilter(empty,
            new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(request("part", "multipart/form-data"),
            new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(request("body", null),
            new MockHttpServletResponse(), new MockFilterChain());
        CachedBodyHttpServletRequestWrapper existing =
            new CachedBodyHttpServletRequestWrapper(request("body", "application/json"), 16);
        filter.doFilter(existing, new MockHttpServletResponse(), new MockFilterChain());

        properties.setMaxCachedRequestBodySize(2);
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(request("large", "application/json"), rejected, new MockFilterChain());
        assertThat(rejected.getStatus()).isEqualTo(413);
    }

    @Test
    void loggingFilterReusesPlatformCachedRequest() throws Exception {
        PlatformWebProperties properties = new PlatformWebProperties();
        properties.setIncludePayload(true);
        CachedBodyHttpServletRequestWrapper request =
            new CachedBodyHttpServletRequestWrapper(
                request("payload", "application/json"), 32);
        new RequestResponseLoggingFilter(properties).doFilter(
            request,
            new MockHttpServletResponse(),
            (current, response) -> assertThat(current).isSameAs(request)
        );
    }

    private static MockHttpServletRequest request(String body, String contentType) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/body");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContentType(contentType);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static ReadListener listener(
        AtomicBoolean data,
        AtomicBoolean complete,
        AtomicBoolean failed,
        boolean throwOnData
    ) {
        return new ReadListener() {
            @Override
            public void onDataAvailable() throws IOException {
                data.set(true);
                if (throwOnData) {
                    throw new IOException("test");
                }
            }
            @Override public void onAllDataRead() { complete.set(true); }
            @Override public void onError(Throwable throwable) { failed.set(true); }
        };
    }
}
