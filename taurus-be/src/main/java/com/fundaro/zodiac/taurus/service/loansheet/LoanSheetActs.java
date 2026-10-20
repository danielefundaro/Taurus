package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Issue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Parts of a template that belong to each act signed electronically. Every direct child of {@code <body>} declares the
 * acts it belongs to with {@code data-taurus-act}; the block of the clauses approved specifically is marked with
 * {@code data-taurus-sign="clauses"}. A template without markers stays usable on paper.
 */
public final class LoanSheetActs {

    public static final String ACT_ATTRIBUTE = "data-taurus-act";
    public static final String SIGN_ATTRIBUTE = "data-taurus-sign";
    public static final String CLAUSES = "clauses";
    public static final String ISSUE = "issue";
    public static final String VARIATION = "variation";
    public static final String RETURN = "return";
    public static final String ALL = "all";
    static final List<String> ACTS = List.of(ISSUE, VARIATION, RETURN);
    static final Set<String> VALUES = Set.of(ISSUE, VARIATION, RETURN, ALL);
    static final String CLAUSES_SELECTOR = "[" + SIGN_ATTRIBUTE + "=" + CLAUSES + "]";

    private LoanSheetActs() {
    }

    /**
     * Keeps in the document only the children of the body that declare the act or {@code all}; text directly in the
     * body belongs to no act.
     */
    public static void filter(Document document, String act) {
        for (Node child : new ArrayList<>(document.body().childNodes())) {
            if (child instanceof Element element) {
                Set<String> values = values(element);
                if (!values.contains(act) && !values.contains(ALL)) element.remove();
            } else if (child instanceof TextNode text && !text.isBlank()) {
                text.remove();
            }
        }
    }

    /**
     * Text of the block of the clauses in a filled template, with normalized spaces; {@code null} when missing.
     */
    public static String clausesText(String html) {
        Element clauses = Jsoup.parse(html).selectFirst(CLAUSES_SELECTOR);
        if (clauses == null) return null;
        String text = clauses.text().strip();
        return text.isEmpty() ? null : text;
    }

    /**
     * Issues that make a template not signable, checked by {@link LoanTemplateValidator#signatureIssues}.
     */
    public static List<Issue> issues(String content) {
        return LoanTemplateValidator.signatureIssues(content);
    }

    public static boolean signable(String content) {
        return issues(content).isEmpty();
    }

    static Set<String> values(Element element) {
        return new LinkedHashSet<>(rawValues(element));
    }

    static List<String> rawValues(Element element) {
        String value = element.attr(ACT_ATTRIBUTE).strip();
        if (value.isEmpty()) return List.of();
        return Arrays.stream(value.split("\\s+")).toList();
    }
}
