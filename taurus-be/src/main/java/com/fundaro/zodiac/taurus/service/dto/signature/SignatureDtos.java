package com.fundaro.zodiac.taurus.service.dto.signature;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanHolderRole;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActStatus;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidenceMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignatureMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.domain.signature.SignerRole;
import com.fundaro.zodiac.taurus.domain.signature.SignerStatus;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanTemplateDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Requests and responses of the electronic signature.
 */
public final class SignatureDtos {

    private SignatureDtos() {
    }

    /**
     * Methods available to the signer. {@code enabled} tells whether the tenant requires the electronic signature;
     * {@code preference} is the method chosen in the profile and {@code proposed} the one the signature dialog offers,
     * the e-mail code while the authentication app is not configured. {@code totpAvailable} is false when the
     * installation does not offer the app or Keycloak does not answer.
     */
    public record Methods(
        boolean enabled,
        SignatureMethod preference,
        SignatureMethod proposed,
        boolean totpAvailable,
        boolean totpConfigured,
        String maskedEmail
    ) {
    }

    public record MethodPreference(@NotNull SignatureMethod preference) {
    }

    /**
     * One element confirmed by a code; the fields depend on the purpose. For {@code ASSIGNMENT_DECISION}: the
     * assignment, the hash of the revision shown to the user, the decision and, for a rejection, its reason. For the
     * signatures of a loan sheet: the act, the document and the hash shown to the signer. For
     * {@code PAPER_DECLARATION}: the act and the note of the declaration.
     */
    public record ChallengeItem(
        Long assignmentId,
        @Pattern(regexp = "^[0-9a-f]{64}$") String revisionHash,
        InventoryDecisionType decision,
        @Size(max = 2000) String rejectionReason,
        Long actId,
        SignatureDocumentRole documentRole,
        @Pattern(regexp = "^[0-9a-f]{64}$") String documentHash,
        @Size(max = 2000) String note
    ) {
        public ChallengeItem(Long assignmentId, String revisionHash, InventoryDecisionType decision, String rejectionReason) {
            this(assignmentId, revisionHash, decision, rejectionReason, null, null, null, null);
        }

        public static ChallengeItem document(Long actId, SignatureDocumentRole role, String hash) {
            return new ChallengeItem(null, null, null, null, actId, role, hash, null);
        }

        public static ChallengeItem paper(Long actId, String note) {
            return new ChallengeItem(null, null, null, null, actId, null, null, note);
        }
    }

    public record ChallengeRequest(
        @NotNull SignaturePurpose purpose,
        @NotNull SignatureMethod method,
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid ChallengeItem> items
    ) {
    }

    /**
     * An open confirmation: where the code was sent, the reference printed in the message and the declarations the
     * signer confirms.
     */
    public record Challenge(
        Long id,
        SignaturePurpose purpose,
        SignatureMethod method,
        String maskedEmail,
        String reference,
        ZonedDateTime expiresAt,
        ZonedDateTime resendAvailableAt,
        int remainingSends,
        int remainingAttempts,
        List<String> declarations
    ) {
    }

    public record ConfirmRequest(@NotNull @Pattern(regexp = "^\\d{6}$") String code) {
        @Override
        public String toString() {
            return "ConfirmRequest[code=******]";
        }
    }

    public record ActOutcome(Long actId, SignatureActType type, SignatureActStatus status, Long assignmentId) {
    }

    public record Confirmation(Long challengeId, List<ActOutcome> acts) {
    }

    /**
     * Result of the verification of the evidence chain of the tenant; {@code problem} is {@code sequence},
     * {@code previousHash}, {@code evidenceHash}, {@code personalData} or {@code head}.
     */
    public record ChainVerification(boolean valid, long checked, Long firstInvalidSequence, String problem) {
    }

    /**
     * Electronic signature settings of the tenant, with the requirements of the installation and the issues of the
     * signature markers of the template in use: with issues, sheets can only be issued on paper.
     */
    public record Settings(boolean enabled, Requirements requirements, List<InventoryLoanTemplateDtos.Issue> templateIssues) {
        public boolean templateSignable() {
            return templateIssues.isEmpty();
        }
    }

    /**
     * What the installation provides: an SMTP server, the secret of the codes and a seal certificate.
     */
    public record Requirements(boolean mail, boolean codes, boolean seal) {
        public boolean satisfied() {
            return mail && codes && seal;
        }

        public List<String> missing() {
            List<String> missing = new java.util.ArrayList<>();
            if (!mail) missing.add("mail");
            if (!codes) missing.add("codes");
            if (!seal) missing.add("seal");
            return missing;
        }
    }

    public record SettingsUpdate(@NotNull Boolean enabled, long entityVersion) {
    }

    /**
     * Data of sections 1 and 2 of the sheet, entered by the holder before signing the issue; all required except the
     * phone. {@code noTaxCode} declares that the holder has no Italian tax code. A parent or guardian, who has no
     * account, enters also the own name and date of birth and the birthplace of the minor.
     */
    public record HolderDataRequest(
        @Size(max = 255) String birthPlace,
        @Size(max = 255) String residence,
        @Size(max = 16) String postalCode,
        @Size(max = 255) String cityProvince,
        @Size(max = 16) String taxCode,
        boolean noTaxCode,
        @Size(max = 255) String document,
        @Size(max = 255) String documentIssuedBy,
        LocalDate documentIssuedOn,
        @Size(max = 32) String phone,
        @Size(max = 255) String minorBirthPlace,
        @Size(max = 100) String holderFirstName,
        @Size(max = 100) String holderLastName,
        LocalDate holderBirthDate
    ) {
        /**
         * The data of a holder who is the adult member.
         */
        public HolderDataRequest(
            String birthPlace,
            String residence,
            String postalCode,
            String cityProvince,
            String taxCode,
            boolean noTaxCode,
            String document,
            String documentIssuedBy,
            LocalDate documentIssuedOn,
            String phone,
            String minorBirthPlace
        ) {
            this(birthPlace, residence, postalCode, cityProvince, taxCode, noTaxCode, document, documentIssuedBy, documentIssuedOn, phone, minorBirthPlace, null, null, null);
        }

        @Override
        public String toString() {
            return "HolderDataRequest[noTaxCode=" + noTaxCode + "]";
        }
    }

    /**
     * A document of an act: the frozen hash shown to the signers and, once the act is signed, the hash of the sealed
     * file; {@code available} is false once Taurus has removed the files.
     */
    public record ActDocument(SignatureDocumentRole role, String frozenHash, String sealedHash, boolean available) {
    }

    /**
     * A signer of an act. A parent or guardian has no account: {@code maskedEmail} is the address of the links and
     * {@code link} the last link sent, shown only to the administrators.
     */
    public record ActSigner(
        Long id,
        SignerRole role,
        String userId,
        String displayName,
        boolean required,
        SignerStatus status,
        ZonedDateTime signedAt,
        List<SignaturePurpose> signedPurposes,
        String maskedEmail,
        LinkView link
    ) {
    }

    /**
     * The last link sent to a signer without an account: {@code sentAt} is empty while the e-mail has not been accepted
     * by the SMTP server, {@code active} is false once the link is expired or revoked.
     */
    public record LinkView(String fingerprint, ZonedDateTime sentAt, ZonedDateTime expiresAt, ZonedDateTime lastUsedAt, boolean active) {
    }

    /**
     * An act of a loan sheet. {@code pendingPurposes} are the confirmations still expected from the authenticated user
     * and {@code dataRequired} tells that the holder has to enter the data of the issue first. {@code holderRole} tells
     * whether the holder is the adult member or a parent or guardian. {@code cieSignAvailable} tells that the user, as
     * holder with an Italian tax code, can sign the pending documents with CieSign.
     */
    public record Act(
        Long id,
        SignatureActType type,
        SignatureActStatus status,
        Long sheetId,
        String sheetNumber,
        InventoryLoanHolderRole holderRole,
        String operation,
        ZonedDateTime createdAt,
        ZonedDateTime completedAt,
        ZonedDateTime purgeAfter,
        ZonedDateTime purgedAt,
        String paperNote,
        List<ActDocument> documents,
        List<ActSigner> signers,
        boolean attestationAvailable,
        boolean associationSignerChangeable,
        boolean dataRequired,
        List<SignaturePurpose> pendingPurposes,
        boolean cieSignAvailable,
        long entityVersion
    ) {
    }

    public record AssociationSignerOption(String userId, String displayName) {
    }

    public record AssociationSignerChange(@NotBlank @Size(max = 255) String userId, long entityVersion) {
    }

    /**
     * Declaration of paper signature; the administrator confirms it with a code of the chosen method, by e-mail when
     * empty.
     */
    public record PaperDeclarationRequest(@NotBlank @Size(max = 2000) String note, SignatureMethod method) {
    }

    /**
     * An evidence of an act, as shown to the administrators; personal data are empty once removed. A CieSign signature
     * has the data of the certificate and the outcome of the revocation check.
     */
    public record Evidence(
        long sequence,
        SignaturePurpose purpose,
        SignatureEvidenceMethod method,
        SignerRole signerRole,
        String signerName,
        String maskedEmail,
        String declaration,
        SignatureDocumentRole documentRole,
        String documentHash,
        ZonedDateTime signedAt,
        String ipAddress,
        String userAgent,
        String certificateSubject,
        String certificateIssuer,
        String certificateSerial,
        String certificateTaxCode,
        String revocationCheck,
        String evidenceHash,
        boolean purged
    ) {
    }

    /**
     * Signature data of an issue proposal: whether the tenant signs electronically, whether the template in use is
     * signable and who can sign for the association, with the default signer.
     */
    public record IssueOptions(
        boolean enabled,
        boolean templateSignable,
        List<AssociationSignerOption> associationSigners,
        String defaultAssociationSignerId
    ) {
    }

    /**
     * A file of an act to download.
     */
    public record ActFile(String fileName, byte[] bytes) {
    }

    /**
     * The act opened by a signature link, as the public page shows it: the tenant, the minor the parent signs for, where
     * the codes are sent and until when the link is valid. Signers carry no identifiers.
     */
    public record LinkAct(String tenantName, String minorName, String maskedEmail, ZonedDateTime linkExpiresAt, Act act) {
    }

    /**
     * A confirmation requested from the public page: the code is always sent to the address of the link, for the
     * document of the act whose hash the parent was shown.
     */
    public record LinkChallengeRequest(@NotNull SignaturePurpose purpose, @NotNull @Pattern(regexp = "^[0-9a-f]{64}$") String documentHash) {
    }
}
