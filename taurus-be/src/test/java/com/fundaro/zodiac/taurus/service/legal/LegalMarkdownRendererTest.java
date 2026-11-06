package com.fundaro.zodiac.taurus.service.legal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The text of a legal document reaches a public page: only the allowed constructs survive, whatever the author
 * writes.
 */
class LegalMarkdownRendererTest {

    private final LegalMarkdownRenderer renderer = new LegalMarkdownRenderer();

    @Test
    void rendersTheAllowedConstructs() {
        String html = renderer.render(
            """
            ## 1. Soggetti

            Testo con **grassetto**, *corsivo* e `codice`.

            - primo
            - secondo

            3. terzo
            4. quarto

            > citazione

            ---

            | Dato | Conservazione |
            | --- | --- |
            | Log | 5 giorni |
            """
        );

        assertThat(html)
            .contains("<h2 id=\"1-soggetti\">1. Soggetti</h2>")
            .contains("<strong>grassetto</strong>", "<em>corsivo</em>", "<code>codice</code>")
            .contains("<ul>", "<li>primo</li>")
            .contains("<ol start=\"3\">")
            .contains("<blockquote>", "<hr>")
            .contains("<table>", "<th>Dato</th>", "<td>5 giorni</td>");
    }

    @Test
    void showsWrittenHtmlAsText() {
        String html = renderer.render("Prima <script>alert(1)</script> dopo <img src=x onerror=alert(1)>\n\n<div onclick=\"x()\">blocco</div>");

        assertThat(html)
            .doesNotContain("<script", "<img", "<div")
            .contains("&lt;script&gt;alert(1)&lt;/script&gt;", "&lt;img src=x onerror=alert(1)&gt;", "&lt;div onclick=");
    }

    @Test
    void keepsOnlySafeLinks() {
        String html = renderer.render(
            "[web](https://example.org) [mail](mailto:info@example.org) [sezione](#2-dati) [js](javascript:alert(1)) " +
            "[dati](data:text/html;base64,PHNjcmlwdD4=) [chiaro](http://example.org) [relativo](/legal/privacy)"
        );

        assertThat(html)
            .contains("<a href=\"https://example.org\" target=\"_blank\" rel=\"noopener noreferrer\">web</a>")
            .contains("<a href=\"mailto:info@example.org\">mail</a>")
            .contains("<a href=\"#2-dati\">sezione</a>")
            .doesNotContain("javascript:", "data:", "http://example.org", "/legal/privacy\"");
        assertThat(html).contains("js", "dati", "chiaro", "relativo");
    }

    @Test
    void removesImages() {
        String html = renderer.render("![logo](https://example.org/logo.png) testo");

        assertThat(html).doesNotContain("<img", "example.org/logo.png").contains("testo");
    }

    @Test
    void makesTheHighestHeadingTheSecondLevel() {
        String html = renderer.render("# Primo\n\n## Secondo\n\n### Terzo\n\n#### Quarto\n\n##### Quinto");

        assertThat(html)
            .contains("<h2 id=\"primo\">Primo</h2>")
            .contains("<h3 id=\"secondo\">Secondo</h3>")
            .contains("<h4 id=\"terzo\">Terzo</h4>")
            .contains("<h4 id=\"quarto\">Quarto</h4>")
            .contains("<h4 id=\"quinto\">Quinto</h4>")
            .doesNotContain("<h1", "<h5");
    }

    @Test
    void keepsTheSecondLevelWhenTheTextStartsThere() {
        assertThat(renderer.render("### Unico")).isEqualTo("<h2 id=\"unico\">Unico</h2>");
    }

    @Test
    void rendersNothingForAnEmptyText() {
        assertThat(renderer.render(null)).isEmpty();
        assertThat(renderer.render("  \n")).isEmpty();
    }

    @Test
    void normalizesTheTextBeforeItsFingerprint() {
        String normalized = LegalContent.normalize("Riga uno\r\nRiga due\rRiga tre  \n\n");

        assertThat(normalized).isEqualTo("Riga uno\nRiga due\nRiga tre");
        assertThat(LegalContent.sha256(normalized)).hasSize(64).isEqualTo(LegalContent.sha256("Riga uno\nRiga due\nRiga tre"));
        assertThat(LegalContent.sha256("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void acceptsOnlyVersionsSafeInAnAddress() {
        assertThat(LegalContent.isValidVersion("1.3")).isTrue();
        assertThat(LegalContent.isValidVersion("2026-1_b")).isTrue();
        assertThat(LegalContent.isValidVersion("a".repeat(32))).isTrue();
        assertThat(LegalContent.isValidVersion("a".repeat(33))).isFalse();
        assertThat(LegalContent.isValidVersion(".1")).isFalse();
        assertThat(LegalContent.isValidVersion("1 3")).isFalse();
        assertThat(LegalContent.isValidVersion("1/3")).isFalse();
        assertThat(LegalContent.isValidVersion("")).isFalse();
        assertThat(LegalContent.isValidVersion(null)).isFalse();
    }
}
