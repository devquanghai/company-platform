package com.company.platform.core.configuration.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties("platform.core.jackson")
public class PlatformJacksonProperties {

    /** Bật cấu hình Jackson an toàn và nhất quán của platform. */
    boolean enabled = true;
    /** Tự động loại bỏ khoảng trắng ở đầu và cuối giá trị chuỗi JSON. */
    boolean trimStrings = true;
    /** Từ chối thuộc tính JSON không được khai báo trong class đích. */
    boolean failOnUnknownProperties = true;
    /** Từ chối token dư thừa sau giá trị JSON gốc. */
    boolean failOnTrailingTokens = true;
    /** Từ chối số thực khi kiểu dữ liệu Java đích là số nguyên. */
    boolean failOnFloatToInteger = true;
    /** Từ chối null khi kiểu dữ liệu Java đích là kiểu primitive. */
    boolean failOnNullForPrimitives = true;
    /** Từ chối ép kiểu scalar ngầm định, ví dụ chuỗi sang số hoặc số sang chuỗi. */
    boolean strictScalarCoercion = true;
    /** Cho phép ánh xạ enum không phân biệt chữ hoa và chữ thường. */
    boolean acceptCaseInsensitiveEnums;
    /** Sắp xếp key của Map khi serialize để kết quả JSON ổn định. */
    boolean orderMapEntriesByKeys;
    /**
     * Cho phép Unicode trong giá trị chuỗi. Việc kiểm tra nghiệp vụ thuộc về
     * Bean Validation thay vì bộ giải tuần tự JSON.
     */
    boolean allowUnicode = true;
    /**
     * Cho phép ký tự đặc biệt trong giá trị chuỗi, ví dụ email, URL và mật khẩu.
     */
    boolean allowSpecialCharacters = true;
}
