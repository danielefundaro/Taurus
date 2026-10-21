package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Issue;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoanSheetActsTest {

    private static final String SIGNABLE = """
        <html><body>
        <div data-taurus-act="all">Intestazione</div>
        <div data-taurus-act="issue"><p>Foglio</p><div data-taurus-sign="clauses">Approvo   gli artt. 3
         e 8.</div></div>
        <div data-taurus-act="variation">Registro</div>
        <div data-taurus-act="return issue">Verbale</div>
        </body></html>
        """;

    @Test
    void keepsOnlyThePartsOfTheAct() {
        Document document = Jsoup.parse(SIGNABLE);
        LoanSheetActs.filter(document, LoanSheetActs.VARIATION);

        assertThat(document.body().text()).isEqualTo("Intestazione Registro");

        Document issue = Jsoup.parse(SIGNABLE);
        LoanSheetActs.filter(issue, LoanSheetActs.ISSUE);
        assertThat(issue.body().text()).contains("Foglio").contains("Verbale").doesNotContain("Registro");
    }

    @Test
    void readsTheClausesWithNormalizedSpaces() {
        assertThat(LoanSheetActs.clausesText(SIGNABLE)).isEqualTo("Approvo gli artt. 3 e 8.");
        assertThat(LoanSheetActs.clausesText("<body><p>Nessun blocco</p></body>")).isNull();
    }

    @Test
    void acceptsTheDefaultTemplateAndAMarkedTemplate() {
        assertThat(LoanSheetActs.issues(SIGNABLE)).isEmpty();
        assertThat(LoanSheetActs.issues(new LoanTemplateDefaults().content())).isEmpty();
    }

    @Test
    void reportsTheMarkersThatMakeATemplateNotSignable() {
        List<Issue> unmarked = LoanSheetActs.issues("<html><body>\n<p>Testo</p>\n</body></html>");
        assertThat(unmarked).extracting(Issue::code).containsExactly(
            "signatureActMissing",
            "signatureActEmpty",
            "signatureActEmpty",
            "signatureActEmpty",
            "signatureClausesMissing"
        );
        assertThat(unmarked).allMatch(issue -> !issue.blocking());
        assertThat(unmarked.get(0).args()).isEqualTo(Map.of("tag", "p"));
        assertThat(unmarked.get(0).line()).isEqualTo(2);

        List<Issue> wrong = LoanSheetActs.issues("""
            <body>
            <div data-taurus-act="issue variazione">x</div>
            <div data-taurus-act="variation return"><p data-taurus-sign="clauses">Clausole</p></div>
            </body>
            """);
        assertThat(wrong).extracting(Issue::code).containsExactly("signatureActUnknown", "signatureClausesOutsideIssue");
        assertThat(wrong.get(0).args()).isEqualTo(Map.of("value", "variazione"));

        assertThat(LoanSheetActs.issues("""
            <body>
            <div data-taurus-act="issue"><p data-taurus-sign="clauses"> </p><p data-taurus-sign="clauses">B</p></div>
            <div data-taurus-act="variation return">x</div>
            </body>
            """)).extracting(Issue::code).containsExactly("signatureClausesDuplicate");
        assertThat(LoanSheetActs.issues("""
            <body>
            <div data-taurus-act="issue"><p data-taurus-sign="clauses"> </p></div>
            <div data-taurus-act="variation return">x</div>
            </body>
            """)).extracting(Issue::code).containsExactly("signatureClausesEmpty");
    }
}
