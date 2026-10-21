package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoanSheetSigningTest {

    private static final String HASH = "a".repeat(64);

    @Test
    void theMinorAcknowledgesEveryKindOfAct() {
        assertThat(LoanSheetSigning.declaration(SignaturePurpose.MINOR_ACKNOWLEDGMENT, item(SignatureActType.LOAN_SHEET_ISSUE, null))).isEqualTo(
            "Ho preso visione del foglio di consegna e affidamento in comodato n. 2026/003 (impronta SHA-256 " + HASH +
            "), che il mio genitore o tutore firma come Affidatario."
        );
        assertThat(LoanSheetSigning.declaration(SignaturePurpose.MINOR_ACKNOWLEDGMENT, item(SignatureActType.LOAN_SHEET_VARIATION, "Rinnovo: Clarinetto")))
            .startsWith("Ho preso visione della variazione del foglio di affidamento n. 2026/003 «Rinnovo: Clarinetto» (impronta");
        assertThat(LoanSheetSigning.declaration(SignaturePurpose.MINOR_ACKNOWLEDGMENT, item(SignatureActType.LOAN_SHEET_RETURN, null)))
            .startsWith("Ho preso visione del verbale di riconsegna del foglio di affidamento n. 2026/003 (impronta");
    }

    @Test
    void theHolderAndTheAssociationDeclareTheDocumentTheyRead() {
        assertThat(LoanSheetSigning.declaration(SignaturePurpose.ACCEPTANCE, item(SignatureActType.LOAN_SHEET_ISSUE, null))).isEqualTo(
            "Ho letto il foglio di consegna e affidamento in comodato n. 2026/003 (impronta SHA-256 " + HASH +
            ") e lo accetto, assumendo gli obblighi dell'Affidatario."
        );
        assertThat(LoanSheetSigning.declaration(SignaturePurpose.ASSOCIATION_SIGNATURE, item(SignatureActType.LOAN_SHEET_RETURN, null))).isEqualTo(
            "Firmo per l'Associazione il verbale di riconsegna del foglio di affidamento n. 2026/003 (impronta SHA-256 " + HASH + ")."
        );
    }

    private static LoanSheetSigning.Item item(SignatureActType type, String operation) {
        return new LoanSheetSigning.Item(1L, SignatureDocumentRole.MAIN, HASH, type, "2026/003", operation);
    }
}
