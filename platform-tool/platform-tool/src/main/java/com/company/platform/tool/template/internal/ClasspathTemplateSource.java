package com.company.platform.tool.template.internal;

import com.company.platform.tool.template.api.TemplateNotFoundException;
import com.company.platform.tool.template.api.TemplateSource;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

public final class ClasspathTemplateSource implements TemplateSource {
    private final ResourceLoader resourceLoader;
    public ClasspathTemplateSource(ResourceLoader resourceLoader) { this.resourceLoader = resourceLoader; }
    @Override public Reader open(String templateId) throws IOException {
        Resource resource = resourceLoader.getResource("classpath:/templates/" + templateId + ".mustache");
        if (!resource.exists()) throw new TemplateNotFoundException(templateId, null);
        return new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
    }
}
