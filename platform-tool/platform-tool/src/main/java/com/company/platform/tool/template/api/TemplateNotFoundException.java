package com.company.platform.tool.template.api;

import com.company.platform.tool.api.PlatformToolException;

public final class TemplateNotFoundException extends PlatformToolException {
    public TemplateNotFoundException(String templateId, Throwable cause) { super("TEMPLATE_NOT_FOUND", "Template not found: " + templateId, cause); }
}
