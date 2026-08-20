package com.company.platform.security.web.internal;

import com.company.platform.security.web.api.SecurityProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

public final class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {
    private final SecurityProblemDetailFactory factory;
    private final ObjectMapper objectMapper;

    public ProblemDetailAccessDeniedHandler(SecurityProblemDetailFactory factory, ObjectMapper objectMapper) {
        this.factory = factory;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), factory.create(
            HttpStatus.FORBIDDEN.value(), "Access denied", "The authenticated identity is not authorized"));
    }
}
