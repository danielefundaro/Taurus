package com.fundaro.zodiac.taurus.service.instrumentsuggestion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Normalized forms of the instrument names, shared by the recognition of the parts and the uniqueness check. */
public final class InstrumentNames {

    /** Longest name stored for an instrument and for a suggestion. */
    public static final int MAX_LENGTH = 255;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_WORD = Pattern.compile("[^\\p{L}\\p{N}#]+");
    private static final Pattern ORDINAL_NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])\\d+\\s*[°ºª]?(?![\\p{L}\\p{N}])|[°ºª]");
    private static final Pattern ORDINAL_WORD = Pattern.compile(
        "(?iu)(?<![\\p{L}\\p{N}])(?:i{1,3}|iv|vi{0,3}|primo|secondo|terzo|quarto|prima|seconda|terza|quarta)(?![\\p{L}\\p{N}])\\.?"
    );
    private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[\\s.,;:\\-–—/()\\[\\]]+|[\\s.,;:\\-–—/(\\[]+$");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern KEY_AFTER_IN = Pattern.compile("(?iu)(?<![\\p{L}])(in\\s+)(\\p{L}+[#♭♯]?)");
    private static final Map<String, String> ROMAN = Map.of(
        "i", "1", "ii", "2", "iii", "3", "iv", "4", "v", "5", "vi", "6", "vii", "7", "viii", "8"
    );
    private static final Map<String, String> ORDINAL_WORDS = Map.of(
        "primo", "1", "prima", "1", "secondo", "2", "seconda", "2", "terzo", "3", "terza", "3", "quarto", "4", "quarta", "4"
    );
    /** Written keys: after "in" any of them, alone only the forms with an accidental, which are not ordinary words. */
    private static final Set<String> KEYS = Set.of(
        "do", "c", "reb", "db", "do#", "re", "d", "mib", "eb", "re#", "mi", "e", "fa", "f", "fa#", "solb", "gb", "sol", "g",
        "lab", "ab", "sol#", "la", "a", "sib", "bb", "la#", "si", "b"
    );
    private static final Set<String> STANDALONE_KEYS = Set.of("sib", "mib", "lab", "reb", "solb", "bb", "eb", "ab");

    private InstrumentNames() {}

    /**
     * The form used to keep two instruments from having the same name: lower case, without accents, punctuation and
     * repeated spaces. Ordinals and keys count, so "Clarinetto in Sib" and "Clarinetto in Mib" stay distinct.
     */
    public static String uniqueKey(String name) {
        if (name == null) return "";
        String text = simplified(name);
        return SPACES.matcher(NOT_WORD.matcher(text).replaceAll(" ")).replaceAll(" ").trim();
    }

    /**
     * The words compared by the exact rule: ordinals as numbers ("II" and "2°" are "2") and without the written key, which
     * is compared apart. "Clarinetto 1° in Sib" gives {@code [clarinetto, 1]}.
     */
    static List<String> exactWords(String name) {
        List<String> words = new ArrayList<>();
        if (name == null) return words;
        String[] tokens = NOT_WORD.split(simplified(name));
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index];
            if (token.isEmpty()) continue;
            boolean nextIsKey = index + 1 < tokens.length && KEYS.contains(tokens[index + 1]);
            if ("in".equals(token) && nextIsKey) {
                index++;
                continue;
            }
            if (STANDALONE_KEYS.contains(token)) continue;
            words.add(ROMAN.getOrDefault(token, ORDINAL_WORDS.getOrDefault(token, token)));
        }
        return words;
    }

    /**
     * The name proposed for a line read on a part: without ordinals and final punctuation, with the key and the initial
     * capital letter. "CLARINETTO 1° IN SIB." becomes "Clarinetto in Sib".
     */
    public static String proposedName(String line) {
        if (line == null) return "";
        String text = ORDINAL_NUMBER.matcher(line).replaceAll(" ");
        text = ORDINAL_WORD.matcher(text).replaceAll(" ");
        text = SPACES.matcher(text).replaceAll(" ");
        text = EDGE_PUNCTUATION.matcher(text.trim()).replaceAll("").trim();
        if (text.isEmpty()) return "";
        if (text.equals(text.toUpperCase(Locale.ROOT))) text = text.toLowerCase(Locale.ROOT);
        Matcher key = KEY_AFTER_IN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (key.find()) {
            String written = KEYS.contains(simplified(key.group(2))) ? capitalized(key.group(2)) : key.group(2);
            key.appendReplacement(result, Matcher.quoteReplacement(key.group(1) + written));
        }
        key.appendTail(result);
        return truncated(capitalized(result.toString()));
    }

    /** The value trimmed and cut to {@link #MAX_LENGTH} characters. */
    public static String truncated(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH).trim();
    }

    private static String simplified(String name) {
        // The ordinal indicators º and ª are letters for Unicode: as the degree sign they are not part of a word.
        String text = name.toLowerCase(Locale.ROOT).replace('♭', 'b').replace('♯', '#').replace('º', '°').replace('ª', '°');
        return MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
    }

    private static String capitalized(String value) {
        if (value.isEmpty()) return value;
        int first = value.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(first))) + value.substring(Character.charCount(first));
    }
}
