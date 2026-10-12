package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Issue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class LoanTemplateValidatorTest {

    private static final String RECOMMENDED = "{{foglio.numero}}{{#beni}}{{nome}}{{/beni}}{{durata.mesiMinimi}}";

    private final LoanSheetPdfRenderer renderer = new LoanSheetPdfRenderer();

    @Test
    void acceptsTheDefaultTemplateWithoutIssues() {
        assertThat(LoanTemplateValidator.validate(new LoanTemplateDefaults().content(), renderer)).isEmpty();
    }

    @Test
    void reportsUnknownVariablesAndListFieldsOutsideTheirSectionWithTheLine() {
        List<Issue> issues = validate("<p>{{associazione.nome}}</p>\n<p>{{associazone.nome}}</p>\n<p>{{numero}}</p>\n" + RECOMMENDED);

        assertThat(issues).filteredOn(Issue::blocking).extracting(Issue::code, Issue::line)
            .containsExactly(tuple("unknownVariable", 2), tuple("unknownVariable", 3));
        assertThat(issues.get(1).args()).isEqualTo(Map.of("name", "numero", "list", "beni"));
    }

    @Test
    void resolvesFieldsInsideTheirListAndRootVariablesEverywhere() {
        String template = "{{#riconsegna.beni}}{{numero}} {{bene}} {{associazione.nome}}{{/riconsegna.beni}}"
            + "{{#beni}}{{#durata.scadenzeDiverse}}{{scadenza}}{{/durata.scadenzeDiverse}}{{/beni}}"
            + "{{^riconsegna.importo}}____{{/riconsegna.importo}}{{! commento }}" + RECOMMENDED;

        assertThat(validate(template)).isEmpty();
    }

    @Test
    void rejectsUnbalancedSectionsAndListsUsedAsText() {
        List<Issue> issues = validate("{{#beni}}\n{{/registro}}\n{{#durata.aScadenza}}\n{{registro}}\n");

        assertThat(issues).filteredOn(Issue::blocking).extracting(Issue::code)
            .contains("syntax.mismatchedClose", "syntax.unclosed", "listAsVariable");
        assertThat(issues).filteredOn(issue -> issue.code().equals("syntax.unclosed"))
            .extracting(Issue::args, Issue::line)
            .containsExactly(tuple(Map.of("name", "durata.aScadenza"), 3));
    }

    @Test
    void rejectsUnescapedOutputIncludesAndDelimiterChanges() {
        List<Issue> issues = validate("{{{socio.nome}}} {{& socio.nome}} {{> altro}} {{=<% %>=}}" + RECOMMENDED);

        assertThat(issues).filteredOn(Issue::blocking).extracting(Issue::code)
            .containsExactlyInAnyOrder("unescaped", "unescaped", "include", "delimiters");
    }

    @Test
    void rejectsExternalResourcesAndForbiddenElements() {
        String template = """
            <html><head>
            <link rel="stylesheet" href="https://example.org/style.css">
            <style>
              @import url("https://example.org/more.css");
              @font-face { font-family: Brand; src: url(https://example.org/brand.woff); }
              body { background: url(data:image/png;base64,AAAA); }
            </style>
            </head><body>
            <script>alert(1)</script>
            <img src="https://example.org/logo.png">
            <img src="{{associazione.logo}}">
            <img src="data:image/svg+xml;base64,AAAA">
            <a href="https://example.org/privacy">Informativa</a>
            <div style="background-image: url('file:///etc/passwd')"></div>
            """ + RECOMMENDED + "</body></html>";

        List<Issue> issues = validate(template);

        assertThat(issues).filteredOn(Issue::blocking).extracting(Issue::code).containsExactlyInAnyOrder(
            "forbiddenElement", "externalResource", "import", "externalResource", "fontFace", "externalResource", "forbiddenElement", "externalResource", "unsupportedImage", "externalResource"
        );
        assertThat(issues).filteredOn(issue -> issue.code().equals("forbiddenElement")).extracting(Issue::line).containsExactly(2, 9);
    }

    @Test
    void warnsAboutFontsCharactersAndMissingRecommendedVariables() {
        List<Issue> issues = validate("<style>p { font-family: 'Comic Sans MS', sans-serif; }</style><p>☐ durata ≤ 24 – «ok»</p>");

        assertThat(issues).noneMatch(Issue::blocking);
        assertThat(issues).extracting(Issue::code).containsExactlyInAnyOrder(
            "font", "unsupportedCharacter", "unsupportedCharacter", "missingSheetNumber", "missingGoods", "missingDurations"
        );
    }

    @Test
    void rejectsEmptyFiles() {
        assertThat(validate("  ")).extracting(Issue::code).containsExactly("empty");
        assertThat(validate("caf�" + RECOMMENDED)).filteredOn(Issue::blocking).extracting(Issue::code).containsExactly("encoding");
    }

    private List<Issue> validate(String template) {
        return LoanTemplateValidator.validate(template, renderer);
    }
}
