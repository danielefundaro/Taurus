package com.fundaro.zodiac.taurus.service.loansheet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LoanSheetTextTest {

    @Test
    void detectsCharactersTheStandardFontsCannotPrint() {
        for (char character : "dell’Affidatario «Associazione» € – — è\n".toCharArray()) {
            assertThat(LoanSheetText.printable(character)).isTrue();
        }
        assertThat(LoanSheetText.printable('≤')).isFalse();
        assertThat(LoanSheetText.printable('☐')).isFalse();
        assertThat(LoanSheetText.printable("durata ≤ 24\tmesi 🎺")).isEqualTo("durata ? 24 mesi ??");
    }

    @Test
    void formatsAgreedValuesWithUnitValueForMultipleUnits() {
        assertThat(LoanSheetText.agreedValue(new BigDecimal("1250"), 1)).isEqualTo("1.250,00");
        assertThat(LoanSheetText.agreedValue(new BigDecimal("75"), 2)).isEqualTo("150,00 (75,00 cad.)");
        assertThat(LoanSheetText.agreedValue(null, 2)).isNull();
    }

    @Test
    void takesTheAgreedUnitValueFromTheEstimatedValueOnlyInEuro() {
        assertThat(LoanSheetText.euroUnitValue(new BigDecimal("75"), "EUR")).isEqualByComparingTo("75");
        assertThat(LoanSheetText.euroUnitValue(new BigDecimal("75"), "eur")).isEqualByComparingTo("75");
        assertThat(LoanSheetText.euroUnitValue(new BigDecimal("75"), "USD")).isNull();
        assertThat(LoanSheetText.euroUnitValue(null, "EUR")).isNull();
    }

    @Test
    void joinsOnlyTheValuesThatArePresent() {
        assertThat(LoanSheetText.join(" – ", "Buono", " ", null, "Graffio")).isEqualTo("Buono – Graffio");
        assertThat(LoanSheetText.join(", ", null, "")).isNull();
        assertThat(LoanSheetText.serialOrInventory("SN-1", "INV-1")).isEqualTo("SN-1 / INV-1");
        assertThat(LoanSheetText.serialOrInventory(" ", "INV-1")).isEqualTo("INV-1");
    }
}
