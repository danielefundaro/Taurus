package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Issue;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.Variable;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos.VariableType;
import com.fundaro.zodiac.taurus.web.rest.errors.ErrorArgs;
import com.samskivert.mustache.MustacheException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.select.NodeTraversor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks a template uploaded by the tenant before preview and activation: Mustache syntax and variables, constructs
 * that are not allowed, external resources, fonts and characters the standard PDF fonts cannot print.
 *
 * <p>The generation with the sample data is checked separately by {@link InventoryLoanTemplateService}.
 */
public final class LoanTemplateValidator {

    private static final Logger LOG = LoggerFactory.getLogger(LoanTemplateValidator.class);

    static final int MAX_ISSUES = 100;
    private static final Pattern TAG = Pattern.compile("\\{\\{(.*?)}}", Pattern.DOTALL);
    private static final Pattern ONLY_TAGS = Pattern.compile("(\\s*\\{\\{[^{}]*}}\\s*)+");
    private static final Pattern CSS_URL = Pattern.compile("url\\(\\s*(['\"]?)(.*?)\\1\\s*\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CSS_IMPORT = Pattern.compile("@import\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_FONT_FACE = Pattern.compile("@font-face\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_FONT_FAMILY = Pattern.compile("font-family\\s*:\\s*([^;{}]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MUSTACHE_LINE = Pattern.compile("line (\\d+)");
    private static final Pattern IMAGE_DATA = Pattern.compile("data:image/(png|jpeg|jpg|gif)[;,].*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Set<String> FORBIDDEN_ELEMENTS = Set.of("script", "iframe", "frame", "frameset", "object", "embed", "applet", "link", "base");
    private static final Set<String> RESOURCE_ATTRIBUTES = Set.of(
        "src", "href", "srcset", "poster", "background", "data", "xlink:href", "action", "formaction", "cite", "longdesc", "usemap", "codebase", "archive", "lowsrc", "dynsrc"
    );
    private static final Set<String> FONTS = Set.of("sans-serif", "serif", "monospace", "helvetica", "times", "times-roman", "times new roman", "courier", "courier new", "inherit", "initial");

    private LoanTemplateValidator() {
    }

    public static boolean hasBlockingIssues(List<Issue> issues) {
        return issues.stream().anyMatch(Issue::blocking);
    }

    /**
     * Returns the issues of a template, sorted by line; {@code renderer} checks the Mustache syntax as the generation
     * reads it.
     */
    public static List<Issue> validate(String content, LoanSheetPdfRenderer renderer) {
        List<Issue> issues = new ArrayList<>();
        if (content == null || content.isBlank()) {
            issues.add(blocking("empty", null));
            return issues;
        }
        if (content.indexOf('�') >= 0) {
            issues.add(blocking("encoding", lineOf(content, content.indexOf('�'))));
        }
        Set<String> used = new HashSet<>();
        boolean syntaxValid = checkTags(content, issues, used);
        if (syntaxValid) checkCompilation(content, renderer, issues);
        checkHtml(content, issues);
        checkRecommendations(used, issues);
        issues.sort(Comparator.comparing((Issue issue) -> issue.line() == null ? 0 : issue.line()));
        return issues.size() > MAX_ISSUES ? new ArrayList<>(issues.subList(0, MAX_ISSUES)) : issues;
    }

    private record Section(String name, int line, Set<String> fields) {
    }

    private static boolean checkTags(String content, List<Issue> issues, Set<String> used) {
        boolean valid = true;
        Deque<Section> sections = new ArrayDeque<>();
        Matcher matcher = TAG.matcher(content);
        while (matcher.find()) {
            int line = lineOf(content, matcher.start());
            String inner = matcher.group(1).strip();
            if (inner.isEmpty()) {
                issues.add(blocking("syntax.emptyTag", line));
                valid = false;
                continue;
            }
            char sigil = inner.charAt(0);
            String name = inner.substring(1).strip();
            switch (sigil) {
                case '!' -> {
                }
                case '{', '&' -> {
                    issues.add(blocking("unescaped", ErrorArgs.of("name", name.replace("}", "").strip()), line));
                    valid = false;
                }
                case '>', '<', '$' -> {
                    issues.add(blocking("include", ErrorArgs.of("name", inner), line));
                    valid = false;
                }
                case '=' -> {
                    issues.add(blocking("delimiters", line));
                    valid = false;
                }
                case '#', '^' -> {
                    Optional<Variable> variable = resolve(name, sections, issues, line);
                    variable.ifPresent(value -> used.add(value.name()));
                    Set<String> fields = variable.filter(value -> value.type() == VariableType.LIST).map(value -> LoanSheetVariables.fields(value.name())).orElse(Set.of());
                    sections.push(new Section(name, line, fields));
                }
                case '/' -> {
                    if (sections.isEmpty()) {
                        issues.add(blocking("syntax.unopenedClose", ErrorArgs.of("name", name), line));
                        valid = false;
                    } else if (!sections.peek().name().equals(name)) {
                        Section open = sections.peek();
                        issues.add(blocking("syntax.mismatchedClose", ErrorArgs.of("name", name).with("openName", open.name()).with("openLine", open.line()), line));
                        valid = false;
                        sections.pop();
                    } else {
                        sections.pop();
                    }
                }
                default -> resolve(inner, sections, issues, line).ifPresent(variable -> {
                    used.add(variable.name());
                    if (variable.type() == VariableType.LIST) {
                        issues.add(blocking("listAsVariable", ErrorArgs.of("name", inner), line));
                    }
                });
            }
        }
        while (!sections.isEmpty()) {
            Section open = sections.pop();
            issues.add(blocking("syntax.unclosed", ErrorArgs.of("name", open.name()), open.line()));
            valid = false;
        }
        return valid;
    }

    /**
     * Resolves a name as Mustache does: in the fields of the enclosing lists, from the innermost, then at the root.
     */
    private static Optional<Variable> resolve(String name, Deque<Section> sections, List<Issue> issues, int line) {
        if ((name.equals(".") || name.equals("this")) && !sections.isEmpty()) return Optional.empty();
        for (Section section : sections) {
            if (section.fields().contains(name)) return Optional.of(new Variable(name, VariableType.TEXT, null, null, List.of()));
        }
        Optional<Variable> variable = LoanSheetVariables.find(name);
        if (variable.isPresent()) return variable;
        String list = LoanSheetVariables.GROUPS.stream()
            .flatMap(group -> group.variables().stream())
            .filter(candidate -> candidate.type() == VariableType.LIST && LoanSheetVariables.fields(candidate.name()).contains(name))
            .map(Variable::name)
            .findFirst()
            .orElse(null);
        issues.add(blocking("unknownVariable", ErrorArgs.of("name", name).with("list", list), line));
        return Optional.empty();
    }

    private static void checkCompilation(String content, LoanSheetPdfRenderer renderer, List<Issue> issues) {
        try {
            renderer.compile(content);
        } catch (MustacheException | IllegalArgumentException exception) {
            Matcher line = MUSTACHE_LINE.matcher(String.valueOf(exception.getMessage()));
            // The message of the library is technical: it goes to the log, the template author gets the line.
            LOG.debug("Invalid Mustache syntax: {}", exception.getMessage());
            issues.add(blocking("syntax.invalid", line.find() ? Integer.valueOf(line.group(1)) : null));
        }
    }

    private static void checkHtml(String content, List<Issue> issues) {
        Document document = Jsoup.parse(content, "", Parser.htmlParser().setTrackPosition(true));
        Set<Character> reported = new HashSet<>();
        NodeTraversor.traverse((node, depth) -> {
            if (node instanceof Element element) checkElement(element, issues);
            else if (node instanceof DataNode data && data.parent() != null && "style".equals(data.parent().normalName())) {
                checkCss(data.getWholeData(), lineOf(node), issues);
            } else if (node instanceof TextNode text) {
                checkCharacters(text.getWholeText(), lineOf(node), reported, issues);
            }
        }, document);
    }

    private static void checkElement(Element element, List<Issue> issues) {
        Integer line = lineOf(element);
        String tag = element.normalName();
        if (FORBIDDEN_ELEMENTS.contains(tag)) {
            issues.add(blocking("forbiddenElement", ErrorArgs.of("tag", tag), line));
        }
        for (Attribute attribute : element.attributes()) {
            String name = attribute.getKey().toLowerCase(Locale.ROOT);
            String value = attribute.getValue().strip();
            if (name.equals("style")) {
                checkCss(value, line, issues);
            } else if (RESOURCE_ATTRIBUTES.contains(name) && !allowedReference(tag, name, value)) {
                if (LoanSheetPdfRenderer.isEmbedded(value)) {
                    issues.add(blocking("unsupportedImage", line));
                } else {
                    issues.add(blocking("externalResource", ErrorArgs.of("where", name).with("value", abbreviate(value)), line));
                }
            }
        }
    }

    private static boolean allowedReference(String tag, String attribute, String value) {
        if (value.isEmpty() || value.startsWith("#") || ONLY_TAGS.matcher(value).matches()) return true;
        if (attribute.equals("href") && tag.equals("a")) {
            String lower = value.toLowerCase(Locale.ROOT);
            return lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:");
        }
        return attribute.equals("src") && IMAGE_DATA.matcher(value).matches();
    }

    private static void checkCss(String css, Integer line, List<Issue> issues) {
        Matcher url = CSS_URL.matcher(css);
        while (url.find()) {
            String value = url.group(2).strip();
            if (!LoanSheetPdfRenderer.isEmbedded(value) && !ONLY_TAGS.matcher(value).matches()) {
                issues.add(blocking("externalResource", ErrorArgs.of("where", "CSS").with("value", abbreviate(value)), lineWithin(line, css, url.start())));
            }
        }
        Matcher importRule = CSS_IMPORT.matcher(css);
        while (importRule.find()) {
            issues.add(blocking("import", lineWithin(line, css, importRule.start())));
        }
        Matcher fontFace = CSS_FONT_FACE.matcher(css);
        while (fontFace.find()) {
            issues.add(blocking("fontFace", lineWithin(line, css, fontFace.start())));
        }
        Matcher family = CSS_FONT_FAMILY.matcher(css);
        while (family.find()) {
            for (String font : family.group(1).split(",")) {
                String normalized = font.strip().replace("\"", "").replace("'", "").replace("!important", "").strip().toLowerCase(Locale.ROOT);
                if (!normalized.isEmpty() && !FONTS.contains(normalized)) {
                    issues.add(warning("font", ErrorArgs.of("font", font.strip()), lineWithin(line, css, family.start())));
                }
            }
        }
    }

    private static void checkCharacters(String text, Integer line, Set<Character> reported, List<Issue> issues) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (!LoanSheetText.printable(character) && reported.add(character)) {
                issues.add(warning(
                    "unsupportedCharacter",
                    ErrorArgs.of("character", String.valueOf(character)).with("codePoint", String.format(Locale.ROOT, "U+%04X", (int) character)),
                    lineWithin(line, text, index)
                ));
            }
        }
    }

    private static void checkRecommendations(Set<String> used, List<Issue> issues) {
        if (!used.contains(LoanSheetVariables.SHEET_NUMBER)) {
            issues.add(warning("missingSheetNumber", null, null));
        }
        if (!used.contains(LoanSheetVariables.GOODS)) {
            issues.add(warning("missingGoods", null, null));
        }
        if (LoanSheetVariables.DURATIONS.stream().noneMatch(used::contains)) {
            issues.add(warning("missingDurations", null, null));
        }
    }

    private static Integer lineOf(Node node) {
        return node.sourceRange().isTracked() ? node.sourceRange().start().lineNumber() : null;
    }

    private static int lineOf(String content, int index) {
        int line = 1;
        for (int position = 0; position < index; position++) if (content.charAt(position) == '\n') line++;
        return line;
    }

    private static Integer lineWithin(Integer start, String text, int index) {
        return start == null ? null : start + lineOf(text, index) - 1;
    }

    private static String abbreviate(String value) {
        return value.length() > 60 ? value.substring(0, 57) + "..." : value;
    }

    static Issue blocking(String code, Integer line) {
        return blocking(code, null, line);
    }

    /**
     * An issue that prevents the activation; {@code code} and {@code args} identify it, the frontend writes the text.
     */
    static Issue blocking(String code, ErrorArgs args, Integer line) {
        return new Issue(code, args == null ? Map.of() : args.values(), line, true);
    }

    static Issue warning(String code, ErrorArgs args, Integer line) {
        return new Issue(code, args == null ? Map.of() : args.values(), line, false);
    }
}
