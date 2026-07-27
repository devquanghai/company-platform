package com.company.platform.core.configuration.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties("platform.core.i18n")
public class PlatformCoreI18nProperties {

    /** Bật MessageSource và dịch vụ đa ngôn ngữ của platform. */
    boolean enabled = true;
    /** Danh sách basename của resource bundle, được tìm theo đúng thứ tự khai báo. */
    List<String> basenames = new ArrayList<>(List.of("core_message", "messages"));
    /** Locale mặc định theo chuẩn BCP 47 khi request không cung cấp locale. */
    String defaultLocale = "en";
    /** Cho phép fallback về locale của hệ điều hành đang chạy ứng dụng. */
    boolean fallbackToSystemLocale;
    /** Trả về chính message code khi không tìm thấy nội dung trong resource bundle. */
    boolean useCodeAsDefaultMessage = true;
    /** Thời gian cache nội dung resource bundle. */
    Duration cacheDuration = Duration.ofHours(1);
    /** Bảng mã ký tự dùng để đọc resource bundle. */
    String encoding = StandardCharsets.UTF_8.name();

    public List<String> getBasenames() {
        return List.copyOf(basenames);
    }

    public void setBasenames(List<String> basenames) {
        this.basenames = new ArrayList<>(basenames == null ? List.of() : basenames);
    }
}
