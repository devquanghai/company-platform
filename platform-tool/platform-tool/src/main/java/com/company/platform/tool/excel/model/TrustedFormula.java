package com.company.platform.tool.excel.model;

import java.util.regex.Pattern;

public record TrustedFormula(String expression) {
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_.$!:,() +*/<>=\"'-]{1,8192}");

    public TrustedFormula {
        if (expression == null || expression.startsWith("=") || !SAFE.matcher(expression).matches() || expression.contains("["))
            throw new IllegalArgumentException("Invalid trusted formula");
    }

    public static TrustedFormula of(String expression) {
        return new TrustedFormula(expression);
    }

    public static TrustedFormula sum(String range) {
        return aggregate("SUM", range);
    }

    public static TrustedFormula average(String range) {
        return aggregate("AVERAGE", range);
    }

    public static TrustedFormula min(String range) {
        return aggregate("MIN", range);
    }

    public static TrustedFormula max(String range) {
        return aggregate("MAX", range);
    }

    public static TrustedFormula count(String range) {
        return aggregate("COUNT", range);
    }

    private static TrustedFormula aggregate(String function, String range) {
        if (range == null || !range.matches("[A-Z]{1,3}[1-9][0-9]*:[A-Z]{1,3}[1-9][0-9]*"))
            throw new IllegalArgumentException("Invalid cell range");
        return new TrustedFormula(function + "(" + range + ")");
    }
}
