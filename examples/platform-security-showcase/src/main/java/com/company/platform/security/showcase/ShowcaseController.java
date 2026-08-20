package com.company.platform.security.showcase;

import com.company.platform.security.context.api.CurrentSecurityContext;
import com.company.platform.security.context.api.SecurityPrincipal;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShowcaseController {
    private final CurrentSecurityContext securityContext;

    public ShowcaseController(CurrentSecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    @GetMapping("/public/hello")
    Map<String, String> hello() {
        return Map.of("message", "hello");
    }

    @GetMapping({"/api/me", "/basic/me", "/web/me"})
    SecurityPrincipal me() {
        return securityContext.principal().orElseThrow();
    }

    @GetMapping("/api/admin")
    Map<String, String> admin() {
        return Map.of("message", "admin access granted");
    }

    @GetMapping("/api/tenant/{tenantId}")
    @PreAuthorize("@tenantAuthorization.canAccess(authentication, #tenantId)")
    public Map<String, String> tenant(@PathVariable String tenantId) {
        return Map.of("tenantId", tenantId, "message", "tenant access granted");
    }

    @GetMapping("/internal/health")
    Map<String, String> internalHealth() {
        return Map.of("status", "UP");
    }
}
