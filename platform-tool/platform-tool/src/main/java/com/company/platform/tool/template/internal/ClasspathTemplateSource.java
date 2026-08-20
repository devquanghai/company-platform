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
    private final String prefix;
    private final String suffix;
    private final java.nio.charset.Charset charset;
    public ClasspathTemplateSource(ResourceLoader resourceLoader, String prefix, String suffix, java.nio.charset.Charset charset) { this.resourceLoader = resourceLoader; this.prefix = prefix; this.suffix = suffix; this.charset = charset; }
    @Override public Reader open(String templateId) throws IOException {
        Resource resource = resourceLoader.getResource(prefix + templateId + suffix);
        if (!resource.exists()) throw new TemplateNotFoundException(templateId, null);
        return new InputStreamReader(resource.getInputStream(), charset);
    }
}
