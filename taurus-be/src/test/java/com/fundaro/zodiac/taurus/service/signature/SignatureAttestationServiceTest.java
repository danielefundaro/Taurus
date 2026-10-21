package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidenceMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.domain.signature.SignerRole;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetPdfRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SignatureAttestationServiceTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @Test
    void listsDocumentsAndSignaturesAndIsSealed() throws Exception {
        SignatureSealService sealService = new SignatureSealService(new ApplicationProperties());
        sealService.useKey(SignatureSealService.ephemeralKey());
        SignatureAttestationService service = new SignatureAttestationService(new LoanSheetPdfRenderer(), sealService);

        byte[] pdf = service.create(attestation());

        assertThat(SignatureSealServiceTest.verifiedSigners(pdf)).hasSize(1);
        String text;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
        assertThat(text)
            .contains("Attestazione di firma elettronica")
            .contains("Banda musicale di prova")
            .contains("Emissione del foglio di affidamento")
            .contains("n. 2026/004")
            .contains("12/10/2026 10:15:30 (2026-10-12 08:15:30 UTC)")
            .contains("Approvazione specifica delle clausole")
            .contains("Mario Rossi (Affidatario)")
            .contains("Codice inviato a m***@example.org")
            .contains("Mario Bianchi (Associazione)")
            .contains("Firma con la Carta d'Identità Elettronica")
            .contains("Codice fiscale: BNCMRA80A01H501U")
            .contains("elenco di revoca non raggiungibile")
            .contains("192.0.2.10")
            .contains(sealService.certificateFingerprint())
            .contains("la conservazione degli atti firmati è a cura dell'Associazione");
    }

    @Test
    void omitsTheSheetForAnActWithoutOne() {
        var model = SignatureAttestationService.model(new SignatureAttestationService.Attestation(
            "Banda", SignatureActType.ASSIGNMENT_ACKNOWLEDGMENT, null, 9L, ZonedDateTime.now(ROME), ROME, List.of(), List.of()
        ), "f".repeat(64));

        assertThat(((java.util.Map<?, ?>) model.get("act")).get("sheetNumber")).isNull();
    }

    private static SignatureAttestationService.Attestation attestation() {
        ZonedDateTime signedAt = ZonedDateTime.of(2026, 10, 12, 10, 15, 30, 0, ROME);
        return new SignatureAttestationService.Attestation(
            "Banda musicale di prova",
            SignatureActType.LOAN_SHEET_ISSUE,
            "2026/004",
            41L,
            signedAt,
            ROME,
            List.of(
                new SignatureAttestationService.Document(SignatureDocumentRole.MAIN, "a".repeat(64), "b".repeat(64)),
                new SignatureAttestationService.Document(SignatureDocumentRole.CLAUSES, "c".repeat(64), "d".repeat(64))
            ),
            List.of(
                new SignatureAttestationService.Signature(
                    "Mario Rossi", SignerRole.HOLDER, SignaturePurpose.ACCEPTANCE, SignatureDocumentRole.MAIN,
                    "Accetto il contratto di comodato.", SignatureEvidenceMethod.EMAIL_OTP, signedAt, "mario.rossi@example.org",
                    null, null, null, null, null, "192.0.2.10", "Mozilla/5.0", 7, "e".repeat(64)
                ),
                new SignatureAttestationService.Signature(
                    "Mario Bianchi", SignerRole.ASSOCIATION, SignaturePurpose.ASSOCIATION_SIGNATURE, SignatureDocumentRole.MAIN,
                    "Firmo per l'Associazione.", SignatureEvidenceMethod.CIESIGN, signedAt, null,
                    "CN=Mario Bianchi", "CN=Ministero dell'Interno", "1234", "BNCMRA80A01H501U", "UNAVAILABLE", null, null, 8, "9".repeat(64)
                )
            )
        );
    }
}
