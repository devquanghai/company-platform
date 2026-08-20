package com.company.platform.security.web.api;

import org.springframework.http.ProblemDetail;

@FunctionalInterface
public interface SecurityProblemDetailFactory {
    ProblemDetail create(int status, String title, String detail);
}
