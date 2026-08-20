package com.company.platform.tool.template.api;

import com.company.platform.tool.api.PlatformToolException;

public final class TemplateRenderException extends PlatformToolException {
    public TemplateRenderException(String message) { super("TEMPLATE_RENDER_FAILED", message); }
    public TemplateRenderException(String message, Throwable cause) { super("TEMPLATE_RENDER_FAILED", message, cause); }
}
