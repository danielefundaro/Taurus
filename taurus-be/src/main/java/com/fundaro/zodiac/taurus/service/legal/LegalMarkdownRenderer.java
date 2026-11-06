package com.fundaro.zodiac.taurus.service.legal;

import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.heading.anchor.HeadingAnchorExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Turns the Markdown text of a legal document into the HTML of the public page, of the acceptance page and of the
 * preview. Written HTML is shown as text, links keep only {@code https:}, {@code mailto:} and in-page anchors,
 * images and any other external resource go away. The highest heading becomes {@code h2}, because the title of the
 * document is the only {@code h1} of the page.
 */
@Component
public class LegalMarkdownRenderer {

    private static final int TOP_LEVEL = 2;
    private static final int LAST_LEVEL = 4;

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(), HeadingAnchorExtension.create());

    private static final Safelist SAFELIST = new Safelist()
        .addTags("h2", "h3", "h4", "p", "br", "hr", "strong", "em", "ul", "ol", "li", "blockquote", "code", "pre")
        .addTags("table", "thead", "tbody", "tr", "th", "td", "a")
        .addAttributes("h2", "id")
        .addAttributes("h3", "id")
        .addAttributes("h4", "id")
        .addAttributes("ol", "start")
        .addAttributes("a", "href")
        .addProtocols("a", "href", "https", "mailto", "#");

    private static final Document.OutputSettings OUTPUT = new Document.OutputSettings().prettyPrint(false);

    private final Parser parser = Parser.builder().extensions(EXTENSIONS).build();

    private final HtmlRenderer renderer = HtmlRenderer.builder().extensions(EXTENSIONS).escapeHtml(true).sanitizeUrls(true).build();

    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        Document rendered = Jsoup.parseBodyFragment(renderer.render(parser.parse(markdown)));
        normalizeHeadings(rendered.body());
        Document clean = Jsoup.parseBodyFragment(Jsoup.clean(rendered.body().html(), "", SAFELIST, OUTPUT));
        clean.outputSettings(OUTPUT);
        for (Element link : clean.select("a[href^=https:]")) {
            link.attr("target", "_blank").attr("rel", "noopener noreferrer");
        }
        return clean.body().html();
    }

    private static void normalizeHeadings(Element body) {
        List<Element> headings = body.select("h1, h2, h3, h4, h5, h6");
        int highest = headings.stream().mapToInt(LegalMarkdownRenderer::level).min().orElse(TOP_LEVEL);
        for (Element heading : headings) {
            heading.tagName("h" + Math.min(LAST_LEVEL, level(heading) - highest + TOP_LEVEL));
        }
    }

    private static int level(Element heading) {
        return heading.normalName().charAt(1) - '0';
    }
}
