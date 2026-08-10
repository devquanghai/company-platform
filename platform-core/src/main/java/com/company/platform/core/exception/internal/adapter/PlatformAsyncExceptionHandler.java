package com.company.platform.core.exception.internal.adapter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import java.lang.reflect.Method;
import java.util.Objects;

@Slf4j
public final class PlatformAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {

  /**
   * Handle the given uncaught exception thrown from an asynchronous method.
   *
   * @param ex     the exception thrown from the asynchronous method
   * @param method the asynchronous method
   * @param params the parameters used to invoke the method
   */
  @Override
  public void handleUncaughtException(
      final Throwable ex,
      final Method method,
      final Object... params
  ) {
    Objects.requireNonNull(ex, "exception must not be null");
    String methodName = method == null ? "<unknown>" : method.getName();
    int parameterCount = params == null ? 0 : params.length;
    log.error("Async method {} failed with {} parameter(s)", methodName, parameterCount, ex);
  }
}
