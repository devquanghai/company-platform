package com.company.platform.core.auto_configuration;

import com.company.platform.core.audit.AuditAspect;
import com.company.platform.core.audit.AuditChangeResolver;
import com.company.platform.core.audit.DefaultAuditChangeResolver;
import com.company.platform.core.audit.jpa.SecurityContextAuditorAware;
import com.company.platform.core.auto_configuration.PlatformAuditAutoConfiguration;
import com.company.platform.core.configuration.properties.PlatformAuditProperties;
import com.company.platform.core.auto_configuration.PlatformCoreAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.AuditorAware;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAuditAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            PlatformCoreAutoConfiguration.class,
            PlatformAuditAutoConfiguration.class
        ));

    @Test
    void isOptInAndRegistersOverrideFriendlyAuditInfrastructure() {
        runner.run(context -> assertThat(context)
            .doesNotHaveBean(AuditAspect.class)
            .doesNotHaveBean(AuditorAware.class));

        runner.withPropertyValues(
                "platform.core.audit.enabled=true",
                "platform.core.audit.default-auditor=batch",
                "platform.core.audit.publish-failure-events=false",
                "platform.core.audit.service-name=orders",
                "platform.core.audit.environment=test",
                "platform.core.audit.timezone=Asia/Ho_Chi_Minh")
            .run(context -> {
                assertThat(context).hasSingleBean(AuditAspect.class)
                    .hasSingleBean(AuditChangeResolver.class)
                    .hasSingleBean(AuditorAware.class);
                assertThat(context.getBean(AuditorAware.class))
                    .isInstanceOf(SecurityContextAuditorAware.class);
                PlatformAuditProperties properties =
                    context.getBean(PlatformAuditProperties.class);
                assertThat(properties.isEnabled()).isTrue();
                assertThat(properties.getDefaultAuditor()).isEqualTo("batch");
                assertThat(properties.isPublishFailureEvents()).isFalse();
                assertThat(properties.getServiceName()).isEqualTo("orders");
                assertThat(properties.getEnvironment()).isEqualTo("test");
                assertThat(properties.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
            });

        DefaultAuditChangeResolver custom = new DefaultAuditChangeResolver();
        runner.withPropertyValues("platform.core.audit.enabled=true")
            .withBean(AuditChangeResolver.class, () -> custom)
            .run(context -> assertThat(context.getBean(AuditChangeResolver.class))
                .isSameAs(custom));

        PlatformAuditProperties properties = new PlatformAuditProperties();
        properties.setDefaultAuditor("system");
        assertThat(new PlatformAuditAutoConfiguration.SystemAuditorConfiguration()
            .platformAuditorAware(properties).getCurrentAuditor()).contains("system");
        assertThat(new PlatformAuditAutoConfiguration.JpaAuditingConfiguration()).isNotNull();
    }
}
