package com.company.platform.core.configuration.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties("platform.core.web")
@Validated
public class PlatformWebProperties {

    /** Bật các quy ước Spring MVC chuẩn của platform. */
    boolean enabled = true;
    /** Loại bỏ khoảng trắng đầu và cuối của request parameter trước khi binding. */
    boolean trimRequestParameters = true;
    /** Bật truyền request ID và correlation ID qua MDC và response header. */
    boolean traceFilterEnabled = true;
    /** Bật log tóm tắt request và response theo cấu trúc. */
    boolean requestLoggingEnabled;
    /** Cho phép ghi payload dạng văn bản vào log trong giới hạn cấu hình. */
    boolean includePayload;
    /** Số ký tự payload tối đa được ghi cho mỗi request hoặc response. */
    @PositiveOrZero
    @Max(1_048_576)
    int maxPayloadLength = 4096;
    /** Bật interceptor bổ sung header Server-Timing vào response. */
    boolean serverTimingEnabled = true;
    /** Tự động bổ sung request, correlation và trace metadata cho ApiResponse trả về từ controller. */
    boolean responseMetadataEnabled = true;
    /** Bật cache request body để filter và component phía sau có thể đọc lại. */
    boolean requestCachingEnabled;
    /** Kích thước request body tối đa được caching filter giữ trong bộ nhớ, tính bằng byte. */
    @PositiveOrZero
    @Max(10_485_760)
    int maxCachedRequestBodySize = 1_048_576;
}
