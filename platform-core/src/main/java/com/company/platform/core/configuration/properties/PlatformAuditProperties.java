package com.company.platform.core.configuration.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties("platform.core.audit")
public class PlatformAuditProperties {

    /** Bật cơ chế JPA auditing và phát sự kiện audit cho các phương thức được đánh dấu. */
    boolean enabled;
    /** Tên người thực hiện mặc định khi không xác định được người dùng đã xác thực. */
    @NotBlank
    String defaultAuditor = "system";
    /** Phát sự kiện audit khi thao tác được đánh dấu kết thúc với lỗi. */
    boolean publishFailureEvents = true;
    /** Múi giờ IANA dùng để tạo thời điểm auditing JPA. */
    @NotBlank
    String timezone = "UTC";
}
