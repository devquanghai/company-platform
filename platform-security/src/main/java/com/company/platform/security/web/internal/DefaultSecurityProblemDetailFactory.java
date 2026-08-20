package com.company.platform.security.web.internal;

import com.company.platform.security.web.api.SecurityProblemDetailFactory;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class DefaultSecurityProblemDetailFactory implements SecurityProblemDetailFactory {
    private static final URI UNAUTHORIZED = URI.create("urn:problem-type:authentication-required");
    private static final URI FORBIDDEN = URI.create("urn:problem-type:access-denied");

    @Override
    public ProblemDetail create(int status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
        problem.setTitle(title);
        problem.setType(status == HttpStatus.UNAUTHORIZED.value() ? UNAUTHORIZED : FORBIDDEN);
        return problem;
    }
}
