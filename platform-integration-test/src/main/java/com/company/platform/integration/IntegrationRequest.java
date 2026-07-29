package com.company.platform.integration;

import com.company.platform.logging.annotation.masking.MaskEmail;
import com.company.platform.logging.annotation.masking.MaskPassword;
import com.company.platform.logging.annotation.masking.MaskPhone;
import com.company.platform.logging.annotation.masking.Sensitive;
import com.company.platform.logging.domain.model.MaskingType;
import com.company.platform.logging.domain.model.PiiType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IntegrationRequest {
    @NotBlank
    @MaskEmail
    String email;
    @MaskPassword
    String password;
    @MaskPhone
    String phoneNumber;
    Boolean isActive;
    OffsetDateTime offsetDateTime;
    Instant instant;
    Long corporationId;
    BigDecimal amount;
    @PastOrPresent
    @Sensitive(
        piiType = PiiType.DATE_OF_BIRTH,
        masking = MaskingType.PARTIAL,
        visiblePrefix = 4
    )
    LocalDate dateOfBirth;
}
