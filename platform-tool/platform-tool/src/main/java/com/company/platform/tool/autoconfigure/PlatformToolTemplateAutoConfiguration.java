package com.company.platform.tool.autoconfigure;

import com.company.platform.tool.template.api.HtmlSanitizer;
import com.company.platform.tool.template.api.TemplateRenderer;
import com.company.platform.tool.template.api.TemplateSource;
import com.company.platform.tool.template.internal.ClasspathTemplateSource;
import com.company.platform.tool.template.internal.JsoupHtmlSanitizer;
import com.company.platform.tool.template.internal.MustacheTemplateRenderer;
import com.samskivert.mustache.Mustache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.mustache.autoconfigure.MustacheAutoConfiguration;
import org.springframework.boot.mustache.autoconfigure.MustacheProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.ObjectProvider;
import io.micrometer.observation.ObservationRegistry;

@AutoConfiguration
@AutoConfigureAfter(MustacheAutoConfiguration.class)
@ConditionalOnClass({Mustache.class, org.jsoup.Jsoup.class})
public class PlatformToolTemplateAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    Mustache.Compiler platformToolMustacheCompiler() {
        return Mustache.compiler().escapeHTML(true).defaultValue("");
    }

    @Bean
    @ConditionalOnMissingBean
    TemplateSource platformToolTemplateSource(ResourceLoader loader, MustacheProperties properties) {
        return new ClasspathTemplateSource(loader, properties.getPrefix(), properties.getSuffix(), properties.getCharset());
    }

    @Bean
    @ConditionalOnMissingBean
    HtmlSanitizer platformToolHtmlSanitizer() {
        return new JsoupHtmlSanitizer();
    }

    @Bean
    @ConditionalOnMissingBean
    TemplateRenderer platformToolTemplateRenderer(Mustache.Compiler compiler, TemplateSource source, ObjectProvider<ObservationRegistry> registries) {
        return new MustacheTemplateRenderer(compiler, source, registries.getIfAvailable(() -> ObservationRegistry.NOOP));
    }
}
