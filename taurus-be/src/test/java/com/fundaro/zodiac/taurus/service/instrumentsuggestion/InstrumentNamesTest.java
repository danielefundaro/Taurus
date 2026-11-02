package com.fundaro.zodiac.taurus.service.instrumentsuggestion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentNamesTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = ';',
        value = {
            "Clarinetto 1° in Sib; Clarinetto in Sib",
            "CLARINETTO 1° IN SIB.; Clarinetto in Sib",
            "Flauto II; Flauto",
            "1° Corno in fa; Corno in Fa",
            "Sax tenore 2ª -; Sax tenore",
            "Tromba primo in Si♭; Tromba in Si♭",
            "Clarinetto piccolo mib; Clarinetto piccolo mib",
            "Concerto in memoria; Concerto in memoria",
        }
    )
    void proposesTheLineWithoutOrdinalsWithTheKey(String line, String proposed) {
        assertThat(InstrumentNames.proposedName(line)).isEqualTo(proposed);
    }

    @Test
    void comparesNamesWithoutCaseAccentsPunctuationAndRepeatedSpaces() {
        assertThat(InstrumentNames.uniqueKey("  Clarinètto   in Sib. ")).isEqualTo("clarinetto in sib");
        assertThat(InstrumentNames.uniqueKey("Flicorno-soprano")).isEqualTo(InstrumentNames.uniqueKey("flicorno soprano"));
        assertThat(InstrumentNames.uniqueKey("Clarinetto 1°")).isEqualTo("clarinetto 1");
        assertThat(InstrumentNames.uniqueKey("Clarinetto in Sib")).isNotEqualTo(InstrumentNames.uniqueKey("Clarinetto in Mib"));
        assertThat(InstrumentNames.uniqueKey("Clarinetto 1°")).isNotEqualTo(InstrumentNames.uniqueKey("Clarinetto 2°"));
        assertThat(InstrumentNames.uniqueKey(null)).isEmpty();
    }

    @Test
    void comparesOrdinalsAsNumbersAndLeavesTheKeyApart() {
        assertThat(InstrumentNames.exactWords("Clarinetto 1° in Sib")).containsExactly("clarinetto", "1");
        assertThat(InstrumentNames.exactWords("Flauto II")).containsExactly("flauto", "2");
        assertThat(InstrumentNames.exactWords("Flauto secondo")).containsExactly("flauto", "2");
        assertThat(InstrumentNames.exactWords("Sax contralto 2ª Mib")).containsExactly("sax", "contralto", "2");
        assertThat(InstrumentNames.exactWords("In Sib")).isEmpty();
    }

    @Test
    void keepsNamesWithinTheLengthOfTheColumn() {
        assertThat(InstrumentNames.proposedName("Arpa ".repeat(80))).hasSizeLessThanOrEqualTo(InstrumentNames.MAX_LENGTH);
        assertThat(InstrumentNames.proposedName(null)).isEmpty();
        assertThat(InstrumentNames.proposedName("1°")).isEmpty();
    }
}
