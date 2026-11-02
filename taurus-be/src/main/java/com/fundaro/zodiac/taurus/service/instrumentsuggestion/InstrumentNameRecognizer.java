package com.fundaro.zodiac.taurus.service.instrumentsuggestion;

import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.scoreplayback.InstrumentPlaybackCatalog;
import com.fundaro.zodiac.taurus.service.scoreplayback.InstrumentPlaybackSuggester;
import com.fundaro.zodiac.taurus.service.scoreplayback.InstrumentPlaybackSuggester.Match;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Recognizes the instrument names in the lines read on the first page of a part, against the instruments of the tenant
 * and the reference catalogue {@link InstrumentPlaybackCatalog}. For every name, in order:
 * <ol>
 *     <li>the tenant instrument with the same name, ordinals included and key apart;</li>
 *     <li>otherwise the longest catalogue keyword of the line: the only tenant instrument of the same catalogue entry is
 *     assigned, several become a choice, none a name to add;</li>
 *     <li>a line without keywords is still compared, word by word, with the tenant instruments the catalogue does not
 *     know.</li>
 * </ol>
 * An instrument whose name gives a key different from the key of the line never matches.
 */
public final class InstrumentNameRecognizer {

    /** Separators of several names on the same line: "Clarinetto basso e Fagotto", "Flauto / Ottavino". */
    private static final Pattern SEPARATORS = Pattern.compile("(?iu)\\s+e\\s+|\\s*[,;/&+]\\s*|\\s+[-–]\\s+");

    private final List<Known> known;

    public InstrumentNameRecognizer(Collection<InstrumentsDTO> instruments) {
        List<Known> values = new ArrayList<>();
        for (InstrumentsDTO instrument : instruments) {
            if (instrument == null || instrument.getId() == null || instrument.getName() == null) continue;
            List<String> words = InstrumentNames.exactWords(instrument.getName());
            if (words.isEmpty()) continue;
            values.add(new Known(
                instrument,
                words,
                InstrumentPlaybackSuggester.writtenKey(instrument.getName()),
                InstrumentPlaybackSuggester.match(instrument.getName()).map(Match::entry).orElse(null)
            ));
        }
        values.sort(Comparator.comparing(value -> value.instrument().getName(), String.CASE_INSENSITIVE_ORDER));
        this.known = List.copyOf(values);
    }

    /** One outcome for each instrument name of the line; none for a title, a tempo or a composer. */
    public List<Outcome> recognize(String line) {
        if (line == null || line.isBlank()) return List.of();
        List<String> segments = SEPARATORS.splitAsStream(line).map(String::trim).filter(segment -> !segment.isEmpty()).toList();
        if (segments.size() > 1) {
            // A tenant instrument may itself be called "Flauto/Ottavino".
            Outcome whole = exact(line);
            if (whole != null) return List.of(whole);
        }
        List<Outcome> outcomes = new ArrayList<>();
        for (String segment : segments.size() > 1 ? segments : List.of(line.trim())) {
            Outcome outcome = evaluate(segment);
            if (outcome != null) outcomes.add(outcome);
        }
        return outcomes;
    }

    /** The outcome of a single name, as the list of names to add re-evaluates it with the current instruments. */
    public Outcome evaluate(String name) {
        Outcome exact = exact(name);
        if (exact != null) return exact;
        Integer key = InstrumentPlaybackSuggester.writtenKey(name);
        Match match = InstrumentPlaybackSuggester.match(name).orElse(null);
        if (match != null) {
            List<Known> sameEntry = known.stream().filter(value -> match.entry().equals(value.entry()) && compatible(key, value.key())).toList();
            if (sameEntry.size() == 1) return new Assigned(sameEntry.get(0).instrument());
            return suggested(name, match.keyword(), sameEntry);
        }
        Known outsideCatalogue = outsideCatalogue(InstrumentNames.exactWords(name), key);
        return outsideCatalogue == null ? null : new Assigned(outsideCatalogue.instrument());
    }

    /** The same name: assigned when only one instrument has it, a choice when several do. */
    private Outcome exact(String name) {
        List<String> words = InstrumentNames.exactWords(name);
        if (words.isEmpty()) return null;
        Integer key = InstrumentPlaybackSuggester.writtenKey(name);
        List<Known> same = known.stream().filter(value -> value.words().equals(words) && compatible(key, value.key())).toList();
        if (same.isEmpty()) return null;
        if (same.size() == 1) return new Assigned(same.get(0).instrument());
        return suggested(name, InstrumentPlaybackSuggester.match(name).map(Match::keyword).orElse(null), same);
    }

    /**
     * The rule used before the catalogue, for the tenant instruments it does not know: the same words, then the longest
     * name found in the line, then the first name that holds the whole line. Ordinals do not count.
     */
    private Known outsideCatalogue(List<String> words, Integer key) {
        List<String> line = withoutNumbers(words);
        if (line.isEmpty()) return null;
        Known inLine = null;
        int inLineWords = 0;
        Known holdingLine = null;
        for (Known value : known) {
            if (value.entry() != null || !compatible(key, value.key())) continue;
            List<String> name = withoutNumbers(value.words());
            if (name.isEmpty()) continue;
            if (name.equals(line)) return value;
            if (name.size() > inLineWords && Collections.indexOfSubList(line, name) >= 0) {
                inLine = value;
                inLineWords = name.size();
            }
            if (holdingLine == null && Collections.indexOfSubList(name, line) >= 0) holdingLine = value;
        }
        return inLine != null ? inLine : holdingLine;
    }

    private static Outcome suggested(String name, String keyword, List<Known> candidates) {
        String proposedName = InstrumentNames.proposedName(name);
        String normalizedName = InstrumentNames.uniqueKey(proposedName);
        if (normalizedName.isEmpty()) return null;
        return new Suggested(proposedName, normalizedName, keyword, candidates.stream().map(Known::instrument).toList());
    }

    /** An instrument without a key in its name matches any key, and so does a line without a key. */
    private static boolean compatible(Integer lineKey, Integer instrumentKey) {
        return lineKey == null || instrumentKey == null || Objects.equals(lineKey, instrumentKey);
    }

    private static List<String> withoutNumbers(List<String> words) {
        return words.stream().filter(word -> !word.chars().allMatch(Character::isDigit)).toList();
    }

    private record Known(InstrumentsDTO instrument, List<String> words, Integer key, InstrumentPlaybackCatalog.Entry entry) {}

    /** What a name read on a part becomes. */
    public sealed interface Outcome permits Assigned, Suggested {}

    /** A tenant instrument, assigned to the part. */
    public record Assigned(InstrumentsDTO instrument) implements Outcome {}

    /**
     * A name to add: no tenant instrument matches ({@code candidates} empty, the instrument can be created) or several do
     * (the user chooses among {@code candidates}).
     *
     * @param normalizedName the proposed name in the form of the uniqueness check, used to group the parts
     * @param keyword        catalogue keyword found in the name, {@code null} for a name outside the catalogue
     */
    public record Suggested(String proposedName, String normalizedName, String keyword, List<InstrumentsDTO> candidates) implements Outcome {}
}
