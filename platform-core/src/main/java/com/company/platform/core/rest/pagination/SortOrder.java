package com.company.platform.core.rest.pagination;
import java.util.Objects;

import com.company.platform.core.exception.PlatformValidationException;
import com.company.platform.core.exception.error.ErrorCategory;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@ToString
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class SortOrder {
    String property;
    SortDirection direction;

    public SortOrder(String property, SortDirection direction) {
        if (property == null || property.isBlank()) {
            throw new PlatformValidationException(ErrorCategory.VALIDATION.getErrorCode(), "Property cannot be null or blank");
        }

        this.property = property;
        this.direction = Objects.requireNonNullElse(
            direction,
            SortDirection.ASC
        );
    }
}
