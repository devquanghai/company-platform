package com.company.platform.integration.masking.dto.response;

import com.company.platform.logging.annotation.masking.Sensitive;
import com.company.platform.logging.domain.model.MaskingType;
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
public class MaskingResponse {
    @Sensitive(masking = MaskingType.PARTIAL, visiblePrefix = 4, visibleSuffix = 3)
    String email;
    @Sensitive(masking = MaskingType.FULL)
    String password;
    @Sensitive(masking = MaskingType.PARTIAL, visiblePrefix = 2, visibleSuffix = 2)
    String fullName;
    @Sensitive(masking = MaskingType.PARTIAL, visiblePrefix = 3, visibleSuffix = 3)
    String phoneNumber;
    String dateOfBirth;
    @Min(value = 1)
    Integer age;
    OffsetDateTime cccdDate;
}
