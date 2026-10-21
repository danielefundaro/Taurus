package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidenceMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.domain.signature.SignerRole;

/**
 * Italian labels printed in the code e-mails and in the attestations.
 */
final class SignatureLabels {

    private SignatureLabels() {
    }

    static String act(SignatureActType type) {
        return switch (type) {
            case LOAN_SHEET_ISSUE -> "Emissione del foglio di affidamento";
            case LOAN_SHEET_VARIATION -> "Variazione del registro del foglio di affidamento";
            case LOAN_SHEET_RETURN -> "Verbale di riconsegna";
            case ASSIGNMENT_ACKNOWLEDGMENT -> "Presa visione dell'assegnazione";
        };
    }

    static String document(SignatureDocumentRole role) {
        return switch (role) {
            case MAIN -> "Documento";
            case CLAUSES -> "Approvazione specifica delle clausole";
            case SNAPSHOT -> "Dati dell'assegnazione";
        };
    }

    static String signer(SignerRole role) {
        return switch (role) {
            case HOLDER -> "Affidatario";
            case MINOR -> "Socio minorenne";
            case ASSOCIATION -> "Associazione";
            case ASSIGNEE -> "Assegnatario";
        };
    }

    static String purpose(SignaturePurpose purpose) {
        return switch (purpose) {
            case ACCEPTANCE -> "Accettazione";
            case CLAUSE_APPROVAL -> "Approvazione specifica delle clausole";
            case MINOR_ACKNOWLEDGMENT -> "Presa visione del socio minorenne";
            case ASSOCIATION_SIGNATURE -> "Firma per l'Associazione";
            case ASSIGNMENT_DECISION -> "Presa visione";
            case PAPER_DECLARATION -> "Dichiarazione di firma su carta";
        };
    }

    static String method(SignatureEvidenceMethod method) {
        return switch (method) {
            case EMAIL_OTP -> "Codice via e-mail";
            case TOTP -> "Codice dell'app di autenticazione";
            case CIESIGN -> "Firma con la Carta d'Identità Elettronica";
            case PAPER_DECLARATION -> "Dichiarazione dell'amministratore";
        };
    }
}
