package com.company.platform.tool.file.model;

public enum DigestAlgorithm {
    SHA_256("SHA-256"),
    SHA_512("SHA-512");
    private final String jcaName;

    DigestAlgorithm(String jcaName) {
        this.jcaName = jcaName;
    }

    public String jcaName() {
        return jcaName;
    }
}
