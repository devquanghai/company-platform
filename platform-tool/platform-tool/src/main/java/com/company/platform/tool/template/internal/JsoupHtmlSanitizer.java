package com.company.platform.tool.template.internal;

import com.company.platform.tool.template.api.HtmlSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public final class JsoupHtmlSanitizer implements HtmlSanitizer {
    private final Safelist safelist = Safelist.relaxed()
        .addTags("html", "head", "body", "style", "table", "thead", "tbody", "tfoot", "tr", "th", "td")
        .addAttributes(":all", "class", "style")
        .addProtocols("a", "href", "http", "https", "mailto")
        .addProtocols("img", "src", "cid");
    @Override public String sanitize(String html) { return Jsoup.clean(html == null ? "" : html, "", safelist, new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false)); }
}
