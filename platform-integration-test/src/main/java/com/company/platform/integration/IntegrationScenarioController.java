package com.company.platform.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/platform/integration")
@RequiredArgsConstructor
public class IntegrationScenarioController {
    private final IntegrationScenarioService scenario;

    @GetMapping
    public IntegrationScenarioResult execute(
        @RequestParam(defaultValue = "alice@example.com") String email
    ) {
        log.debug("Executing platform integration scenario");
        return scenario.execute(email);
    }
}
