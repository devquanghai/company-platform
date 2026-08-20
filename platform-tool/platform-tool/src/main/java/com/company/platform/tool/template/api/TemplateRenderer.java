package com.company.platform.tool.template.api;

import java.util.Map;

public interface TemplateRenderer {
    String render(String templateId, Map<String, ?> parameters);
}
