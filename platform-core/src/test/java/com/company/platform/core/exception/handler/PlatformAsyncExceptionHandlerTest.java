package com.company.platform.core.exception.handler;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PlatformAsyncExceptionHandlerTest {

    private final PlatformAsyncExceptionHandler handler = new PlatformAsyncExceptionHandler();

    @Test
    void handlesKnownAndUnknownInvocationMetadataWithoutLoggingValues() throws Exception {
        Method method = getClass().getDeclaredMethod("sample", String.class);
        handler.handleUncaughtException(new IllegalStateException("failed"), method, "secret");
        handler.handleUncaughtException(new IllegalStateException("failed"), null, (Object[]) null);
    }

    @Test
    void rejectsNullException() {
        assertThatNullPointerException()
            .isThrownBy(() -> handler.handleUncaughtException(null, null));
    }

    @SuppressWarnings("unused")
    private void sample(String value) {
        // Method exists only to supply safe reflection metadata to the handler.
    }
}
