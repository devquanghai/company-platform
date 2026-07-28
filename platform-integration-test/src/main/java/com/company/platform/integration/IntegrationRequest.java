package com.company.platform.integration;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IntegrationRequest {
    @NotBlank
    String email;
    String password;
    String phoneNumber;
    Long corporationId;
    BigDecimal amount;
    @PastOrPresent
    LocalDate dateOfBirth;
}
