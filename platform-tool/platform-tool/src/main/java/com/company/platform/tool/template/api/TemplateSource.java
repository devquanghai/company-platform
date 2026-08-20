package com.company.platform.tool.template.api;

import java.io.IOException;
import java.io.Reader;

public interface TemplateSource {
    Reader open(String normalizedTemplateId) throws IOException;
}
