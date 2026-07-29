package com.company.platform.integration;

import com.company.platform.core.rest.response.ApiResponse;
import com.company.platform.logging.annotation.crypto.DecryptValue;
import com.company.platform.logging.annotation.crypto.EncryptResult;
import com.company.platform.logging.annotation.logging.Loggable;
import com.company.platform.logging.api.masking.DataMaskingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/platform/integration")
@RequiredArgsConstructor
@Validated
public class IntegrationScenarioController {
    private static final String INTEGRATION_KEY_ALIAS = "integration-data";

    private final IntegrationScenarioService scenario;
    private final DataMaskingService masking;

    @GetMapping
    public IntegrationScenarioResult execute(
        @RequestParam(defaultValue = "alice@example.com") String email
    ) {
        log.debug("Executing platform integration scenario");
        return scenario.execute(email);
    }

    @PostMapping
    public ApiResponse<IntegrationScenarioResult> executePost(@RequestBody @Valid IntegrationRequest request)
    {
        log.debug("Executing platform integration scenario");
        return ApiResponse.success(scenario.executePost(request));
    }

    @PostMapping("/masking/annotations")
    @Loggable(
        event = "INTEGRATION_ANNOTATION_MASKING",
        includeArguments = true,
        includeResult = true
    )
    public Object maskAnnotatedRequest(
        @RequestBody @Valid IntegrationRequest request
    ) {
        return masking.sanitize(request);
    }

    @PostMapping("/crypto/encrypt")
    @EncryptResult(keyAlias = INTEGRATION_KEY_ALIAS)
    public String encryptResult(@RequestBody String plaintext) {
        return plaintext;
    }

    @PostMapping("/crypto/decrypt")
    public Map<String, String> decryptArgument(
        @RequestBody
        @DecryptValue(keyAlias = INTEGRATION_KEY_ALIAS)
        String ciphertext
    ) {
        return Map.of("plaintext", ciphertext);
    }
}
