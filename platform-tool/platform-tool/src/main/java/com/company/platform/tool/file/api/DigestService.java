package com.company.platform.tool.file.api;

import com.company.platform.tool.file.model.DigestAlgorithm;

import java.io.IOException;
import java.io.InputStream;

public interface DigestService {
    String digest(InputStream input, DigestAlgorithm algorithm) throws IOException;
}
