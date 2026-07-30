package com.company.platform.core.configuration.properties;

import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties("platform.core.openapi")
public class PlatformOpenApiProperties {

    /** Bật bean định nghĩa OpenAPI chuẩn của platform. */
    boolean enabled = true;
    /** Tiêu đề API hiển thị trên Swagger UI hoặc tài liệu OpenAPI. */
    String title = "Platform API";
    /** Phiên bản API được công bố trong tài liệu OpenAPI. */
    String version = "v1";
    /** Mô tả API hiển thị cho người sử dụng tài liệu. */
    String description = "Platform service API";
    /** Kiểu xác thực được khai báo mặc định cho toàn bộ operation. */
    OpenApiAuthenticationType authenticationType = OpenApiAuthenticationType.NONE;
    /** Tên header, query parameter hoặc cookie chứa API key. */
    String apiKeyName = "X-API-KEY";
    /** Vị trí nhận API key trong request. */
    SecurityScheme.In apiKeyLocation = SecurityScheme.In.HEADER;
}
