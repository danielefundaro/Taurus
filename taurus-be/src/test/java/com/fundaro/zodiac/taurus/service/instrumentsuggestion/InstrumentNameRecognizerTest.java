package com.fundaro.zodiac.taurus.service.instrumentsuggestion;

import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.instrumentsuggestion.InstrumentNameRecognizer.Assigned;
import com.fundaro.zodiac.taurus.service.instrumentsuggestion.InstrumentNameRecognizer.Outcome;
import com.fundaro.zodiac.taurus.service.instrumentsuggestion.InstrumentNameRecognizer.Suggested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstrumentNameRecognizerTest {

    @Test
    void assignsTheInstrumentWithTheSameName() {
        InstrumentNameRecognizer recognizer = recognizer("Clarinetto 1°", "Clarinetto 2°", "Flauto 1°", "Flauto 2°");

        assertThat(assigned(recognizer, "Clarinetto 1° in Sib")).isEqualTo("Clarinetto 1°");
        assertThat(assigned(recognizer, "Flauto II")).isEqualTo("Flauto 2°");
        assertThat(assigned(recognizer, "FLAUTO PRIMO")).isEqualTo("Flauto 1°");
    }

    @Test
    void assignsTheOnlyInstrumentOfTheKeyword() {
        InstrumentNameRecognizer recognizer = recognizer("Clarinetto 1°", "Tromba", "Sax contralto", "Euphonium");

        assertThat(assigned(recognizer, "Clarinetto in Sib")).isEqualTo("Clarinetto 1°");
        assertThat(assigned(recognizer, "Tromba 2° in Sib")).isEqualTo("Tromba");
        assertThat(assigned(recognizer, "Sassofono alto")).isEqualTo("Sax contralto");
        assertThat(assigned(recognizer, "Flicorno baritono")).isEqualTo("Euphonium");
        assertThat(assigned(recognizer, "Cl. in Sib")).isEqualTo("Clarinetto 1°");
    }

    @Test
    void offersTheChoiceWhenSeveralInstrumentsHaveTheKeyword() {
        InstrumentNameRecognizer recognizer = recognizer("Corno 1°", "Corno 2°", "Flicorno soprano 1°");

        Suggested suggested = suggested(recognizer, "Corno in Mib");

        assertThat(suggested.candidates()).extracting(InstrumentsDTO::getName).containsExactly("Corno 1°", "Corno 2°");
        assertThat(suggested.proposedName()).isEqualTo("Corno in Mib");
        assertThat(suggested.keyword()).isEqualTo("corno");
    }

    @Test
    void proposesToCreateAnInstrumentOfTheCatalogueTheTenantDoesNotHave() {
        InstrumentNameRecognizer recognizer = recognizer("Clarinetto");

        Suggested suggested = suggested(recognizer, "Clarinetto basso");

        assertThat(suggested.candidates()).isEmpty();
        assertThat(suggested.proposedName()).isEqualTo("Clarinetto basso");
        assertThat(suggested.normalizedName()).isEqualTo("clarinetto basso");
        assertThat(suggested(recognizer("Basso 1°"), "Contrabbasso").keyword()).isEqualTo("contrabbasso");
    }

    @Test
    void neverMatchesAnInstrumentWithAnotherKey() {
        assertThat(suggested(recognizer("Clarinetto in Mib"), "Clarinetto in Sib").candidates()).isEmpty();
        assertThat(suggested(recognizer("Clarinetto in Mib"), "Clarinetto in Sib").proposedName()).isEqualTo("Clarinetto in Sib");
        assertThat(assigned(recognizer("Clarinetto in Mib", "Clarinetto in Sib"), "Clarinetto in Sib")).isEqualTo("Clarinetto in Sib");
        assertThat(assigned(recognizer("Clarinetto 1°"), "Clarinetto 1° in Sib")).isEqualTo("Clarinetto 1°");
    }

    @Test
    void comparesTheLinesWithoutKeywordsOnlyWithTheInstrumentsOutsideTheCatalogue() {
        InstrumentNameRecognizer recognizer = recognizer("Voce solista", "Flauto 1°");

        assertThat(assigned(recognizer, "Voce")).isEqualTo("Voce solista");
        assertThat(assigned(recognizer, "Voce solista 2")).isEqualTo("Voce solista");
        assertThat(recognizer.recognize("Allegro moderato")).isEmpty();
        assertThat(recognizer.recognize("Arpeggio")).isEmpty();
        assertThat(recognizer.recognize("Tr.")).isEmpty();
        assertThat(recognizer.recognize("1°")).isEmpty();
        assertThat(recognizer.recognize(" ")).isEmpty();
    }

    @Test
    void givesOneOutcomeForEachNameOfTheLine() {
        InstrumentNameRecognizer recognizer = recognizer("Fagotto");

        List<Outcome> outcomes = recognizer.recognize("Clarinetto basso e Fagotto");

        assertThat(outcomes).hasSize(2);
        assertThat(outcomes.get(0)).isInstanceOfSatisfying(Suggested.class, value -> assertThat(value.proposedName()).isEqualTo("Clarinetto basso"));
        assertThat(outcomes.get(1)).isInstanceOfSatisfying(Assigned.class, value -> assertThat(value.instrument().getName()).isEqualTo("Fagotto"));
        assertThat(recognizer("Flauto/Ottavino").recognize("Flauto / Ottavino")).singleElement().isInstanceOf(Assigned.class);
    }

    @Test
    void recognizesTheAbbreviationsOfTheCatalogue() {
        InstrumentNameRecognizer recognizer = recognizer();

        assertThat(suggested(recognizer, "Sax A. 1°").keyword()).isEqualTo("sax a");
        assertThat(suggested(recognizer, "Fl.").keyword()).isEqualTo("fl");
        assertThat(suggested(recognizer, "Gr. cassa").keyword()).isEqualTo("gr cassa");
    }

    private static String assigned(InstrumentNameRecognizer recognizer, String line) {
        Outcome outcome = recognizer.evaluate(line);
        assertThat(outcome).as(line).isInstanceOf(Assigned.class);
        return ((Assigned) outcome).instrument().getName();
    }

    private static Suggested suggested(InstrumentNameRecognizer recognizer, String line) {
        Outcome outcome = recognizer.evaluate(line);
        assertThat(outcome).as(line).isInstanceOf(Suggested.class);
        return (Suggested) outcome;
    }

    private static InstrumentNameRecognizer recognizer(String... names) {
        long[] id = { 0 };
        return new InstrumentNameRecognizer(Arrays.stream(names).map(name -> {
            InstrumentsDTO instrument = new InstrumentsDTO();
            instrument.setId(++id[0]);
            instrument.setName(name);
            return instrument;
        }).toList());
    }
}
