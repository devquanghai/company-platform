package com.company.platform.tool.template.internal;

import com.company.platform.tool.template.api.TemplateRenderer;
import com.company.platform.tool.template.api.TemplateRenderException;
import com.company.platform.tool.template.api.TemplateNotFoundException;
import com.company.platform.tool.template.api.TemplateSource;
import com.company.platform.tool.common.ToolObservations;
import com.samskivert.mustache.Mustache;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import io.micrometer.observation.ObservationRegistry;

public final class MustacheTemplateRenderer implements TemplateRenderer {
    private final Mustache.Compiler compiler;
    private final TemplateSource source;
    private final ObservationRegistry observations;
    public MustacheTemplateRenderer(Mustache.Compiler compiler, TemplateSource source, ObservationRegistry observations) { this.compiler = compiler.escapeHTML(true); this.source = source; this.observations = observations; }
    @Override public String render(String templateId, Map<String, ?> parameters) {
        return ToolObservations.observe("platform.tool.template.render", "mustache", observations, () -> renderInternal(templateId, parameters));
    }
    private String renderInternal(String templateId, Map<String, ?> parameters) {
        String normalized = SecureTemplateNames.normalize(templateId);
        try (Reader reader = source.open(normalized)) {
            return compiler.compile(reader).execute(parameters == null ? Map.of() : Map.copyOf(parameters));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof TemplateRenderException renderException) throw renderException;
            if (exception instanceof TemplateNotFoundException notFoundException) throw notFoundException;
            throw new TemplateRenderException("Unable to render template " + normalized, exception);
        }
    }
}
