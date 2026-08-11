package com.company.platform.integration.masking.dto.request;

import com.company.platform.logging.annotation.crypto.EncryptResult;
import com.company.platform.logging.annotation.crypto.EncryptValue;
import com.company.platform.logging.domain.model.CryptoProviderType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MaskingRequest {
    @Email
    String email;
    @EncryptValue(provider= CryptoProviderType.JASYPT, keyAlias = "")
    String password;
    String fullName;
    String phoneNumber;
    @PastOrPresent
    LocalDate dateOfBirth;
    @Min(value = 1)
    Integer age;
    OffsetDateTime cccdDate;
}
