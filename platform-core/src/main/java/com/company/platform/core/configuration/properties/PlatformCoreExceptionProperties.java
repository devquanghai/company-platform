package com.company.platform.core.configuration.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties("platform.core.exception-handling")
public class PlatformCoreExceptionProperties {

    /** Bật bộ xử lý exception REST chuẩn của platform. */
    boolean enabled = true;
    /** Cho phép trả lại giá trị bị từ chối trong chi tiết lỗi; nên tắt để tránh lộ dữ liệu nhạy cảm. */
    boolean includeRejectedValue;
}
