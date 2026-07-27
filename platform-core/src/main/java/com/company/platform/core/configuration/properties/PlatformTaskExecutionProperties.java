package com.company.platform.core.configuration.properties;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@ConfigurationProperties("platform.core.task-execution")
public class PlatformTaskExecutionProperties {

    /** Bật executor dùng virtual thread cho tác vụ bất đồng bộ. */
    boolean enabled = true;
    /** Tiền tố dùng để đặt tên virtual thread, hỗ trợ theo dõi log và thread dump. */
    String threadNamePrefix = "platform-vt-";
    /** Truyền MDC, request attributes và SecurityContext sang thread thực thi tác vụ. */
    boolean contextPropagationEnabled = true;
}
