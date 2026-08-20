package com.company.platform.tool.template.internal;

import com.company.platform.tool.template.api.HtmlSanitizer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

public final class JsoupHtmlSanitizer implements HtmlSanitizer {
    private final Safelist safelist = Safelist.relaxed()
        .addTags("style", "table", "thead", "tbody", "tfoot", "tr", "th", "td")
        .addAttributes(":all", "class", "style")
        .addProtocols("a", "href", "http", "https", "mailto")
        .addProtocols("img", "src", "cid", "memory");
    @Override public String sanitize(String html) {
        Document document = Jsoup.parse(html == null ? "" : html);
        document.select("head style").forEach(style -> document.body().prependChild(style.clone()));
        return Jsoup.clean(document.body().html(), "", safelist, new Document.OutputSettings().prettyPrint(false));
    }
}
