package com.fundaro.zodiac.taurus.service.signature;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItalianTaxCodeTest {

    @Test
    void checksFormatAndCheckCharacter() {
        assertThat(ItalianTaxCode.valid("RSSMRA85T10A562S")).isTrue();
        assertThat(ItalianTaxCode.valid(" rssmra85t10a562s ")).isTrue();
        assertThat(ItalianTaxCode.valid("RSSGLI90C54H501M")).isTrue();
        assertThat(ItalianTaxCode.valid("RSSMRA85T10A562X")).isFalse();
        assertThat(ItalianTaxCode.valid("RSSMRA85T10A562")).isFalse();
        assertThat(ItalianTaxCode.valid("RSSMRA85Z10A562S")).isFalse();
        assertThat(ItalianTaxCode.valid(null)).isFalse();
    }

    @Test
    void acceptsTheLettersThatReplaceDigitsInHomonymousCodes() {
        assertThat(ItalianTaxCode.valid("RSSMRA85T10A56NH")).isTrue();
    }
}
