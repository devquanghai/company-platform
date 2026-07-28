package com.company.platform.integration;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/platform/integration")
@RequiredArgsConstructor
@Validated
public class IntegrationScenarioController {
    private final IntegrationScenarioService scenario;

    @GetMapping
    public IntegrationScenarioResult execute(
        @RequestParam(defaultValue = "alice@example.com") String email
    ) {
        log.debug("Executing platform integration scenario");
        return scenario.execute(email);
    }

    @PostMapping
    public IntegrationScenarioResult executePost(@RequestBody @Valid IntegrationRequest request)
    {
        log.debug("Executing platform integration scenario");
        return scenario.executePost(request);
    }
}
