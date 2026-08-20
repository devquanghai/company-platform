package com.company.platform.security.web.internal;

import com.company.platform.security.web.api.SecurityProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import tools.jackson.databind.ObjectMapper;

public final class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final SecurityProblemDetailFactory factory;
    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(SecurityProblemDetailFactory factory, ObjectMapper objectMapper) {
        this.factory = factory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), factory.create(
            HttpStatus.UNAUTHORIZED.value(), "Authentication required", "Valid authentication is required"));
    }
}
