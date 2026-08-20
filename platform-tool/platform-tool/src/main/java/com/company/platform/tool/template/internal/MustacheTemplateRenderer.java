package com.company.platform.tool.template.internal;

import com.company.platform.tool.template.api.TemplateRenderer;
import com.company.platform.tool.template.api.TemplateRenderException;
import com.company.platform.tool.template.api.TemplateSource;
import com.samskivert.mustache.Mustache;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;

public final class MustacheTemplateRenderer implements TemplateRenderer {
    private final Mustache.Compiler compiler;
    private final TemplateSource source;
    public MustacheTemplateRenderer(Mustache.Compiler compiler, TemplateSource source) { this.compiler = compiler.escapeHTML(true); this.source = source; }
    @Override public String render(String templateId, Map<String, ?> parameters) {
        String normalized = SecureTemplateNames.normalize(templateId);
        try (Reader reader = source.open(normalized)) {
            return compiler.compile(reader).execute(parameters == null ? Map.of() : Map.copyOf(parameters));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof TemplateRenderException renderException) throw renderException;
            throw new TemplateRenderException("Unable to render template " + normalized, exception);
        }
    }
}
