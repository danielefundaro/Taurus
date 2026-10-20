package com.fundaro.zodiac.taurus.service.signature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanHolderRole;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheet;
import com.fundaro.zodiac.taurus.domain.signature.SignatureAct;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActSigner;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActStatus;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureChallenge;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocument;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidence;
import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidenceMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignatureMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.domain.signature.SignerRole;
import com.fundaro.zodiac.taurus.domain.signature.SignerStatus;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanSheetRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActSignerRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureDocumentRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureEvidenceRepository;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ActOutcome;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ChallengeItem;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetText;
import com.fundaro.zodiac.taurus.service.signature.SignaturePurposeHandler.Prepared;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Signatures of the acts of a loan sheet confirmed by a code: acceptance and approval of the clauses by the holder,
 * signature for the association and acknowledgment of the issue by the minor member. Several variations of the same
 * signer can be confirmed with one code; the issue and the return are confirmed alone, and the acceptance and the
 * approval of the clauses are always distinct. A parent or guardian without an account signs through a link. The holder
 * can also sign a document with CieSign: the signed file replaces the code.
 */
@Component
public class LoanSheetSigning {

    private static final ObjectMapper CANONICAL = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final LoanSheetSignatureService signatureService;
    private final LoanSheetActCompletion completion;
    private final SignatureActSignerRepository signerRepository;
    private final SignatureDocumentRepository documentRepository;
    private final SignatureEvidenceRepository evidenceRepository;
    private final SignatureEvidenceService evidenceService;
    private final InventoryLoanSheetRepository sheetRepository;
    private final LoanSheetActDocuments documents;
    private final SignatureMetrics metrics;
    private final int maxItems;

    public LoanSheetSigning(
        LoanSheetSignatureService signatureService,
        LoanSheetActCompletion completion,
        SignatureActSignerRepository signerRepository,
        SignatureDocumentRepository documentRepository,
        SignatureEvidenceRepository evidenceRepository,
        SignatureEvidenceService evidenceService,
        InventoryLoanSheetRepository sheetRepository,
        LoanSheetActDocuments documents,
        SignatureMetrics metrics,
        ApplicationProperties applicationProperties
    ) {
        this.signatureService = signatureService;
        this.completion = completion;
        this.signerRepository = signerRepository;
        this.documentRepository = documentRepository;
        this.evidenceRepository = evidenceRepository;
        this.evidenceService = evidenceService;
        this.sheetRepository = sheetRepository;
        this.documents = documents;
        this.metrics = metrics;
        this.maxItems = applicationProperties.getSignature().getMaxBatchItems();
    }

    public Prepared prepare(SignaturePurpose purpose, List<ChallengeItem> items, Signer signer) {
        if (items == null || items.isEmpty() || items.size() > maxItems) throw invalid("The signature needs from 1 to " + maxItems + " acts");
        Set<Long> acts = new HashSet<>();
        List<Item> canonical = new ArrayList<>();
        for (ChallengeItem item : items) {
            if (item.actId() == null || item.documentHash() == null) throw invalid("Incomplete signature item");
            if (!acts.add(item.actId())) throw invalid("Duplicate act in the signature");
            canonical.add(check(purpose, item.actId(), item.documentHash(), signer, false).item());
        }
        boolean batch = canonical.size() > 1;
        if (batch && (purpose == SignaturePurpose.CLAUSE_APPROVAL || canonical.stream().anyMatch(item -> item.actType() != SignatureActType.LOAN_SHEET_VARIATION))) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "Only variations can be signed together", "signature.act.batchNotAllowed");
        }
        return describe(purpose, canonical, json(canonical));
    }

    public Prepared describe(SignaturePurpose purpose, String itemsJson) {
        return describe(purpose, items(itemsJson), itemsJson);
    }

    /**
     * Checks every act again with a lock and records the signatures; an act whose signers have all signed is
     * completed.
     */
    public List<ActOutcome> confirm(SignaturePurpose purpose, SignatureChallenge challenge, Signer signer) {
        List<ActOutcome> outcomes = new ArrayList<>();
        SignatureEvidenceMethod method = challenge.getMethod() == SignatureMethod.TOTP ? SignatureEvidenceMethod.TOTP : SignatureEvidenceMethod.EMAIL_OTP;
        for (Item item : items(challenge.getItemsJson())) {
            Checked checked = check(purpose, item.actId(), item.documentHash(), signer, true);
            outcomes.add(record(checked, purpose, method, challenge, null, signer));
        }
        return outcomes;
    }

    /**
     * What the holder signs with CieSign: the frozen document, with the name and the tax code the certificate must
     * have. The approval of the clauses is the CieSign signature of their document.
     */
    public CieSignTarget cieSignTarget(long actId, SignatureDocumentRole role, Signer signer) {
        Checked checked = check(cieSignPurpose(role), actId, null, signer, false);
        InventoryLoanSheet sheet = sheetRepository.findById(checked.act().getSheetId()).orElseThrow(LoanSheetSignatureService::actNotFound);
        if (sheet.getHolderTaxCode() == null) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "The holder has no Italian tax code", "signature.cieSign.notAvailable");
        }
        if (checked.document().getFrozenMediaAssetId() == null) throw LoanSheetSignatureService.purged();
        String name = sheet.getHolderRole() == InventoryLoanHolderRole.GUARDIAN
            ? sheet.getHolderName()
            : LoanSheetText.join(" ", sheet.getMemberName(), sheet.getMemberLastName());
        return new CieSignTarget(
            checked.document().getFrozenHash(),
            documents.read(checked.document().getFrozenMediaAssetId(), signer.tenantCode()),
            new CieSignVerifier.Identity(name, sheet.getHolderTaxCode())
        );
    }

    /**
     * Records the signature of a document with CieSign, verified outside the transaction: the act is checked again
     * with a lock and the document must still be the one the file was verified against.
     */
    public ActOutcome recordCieSign(long actId, SignatureDocumentRole role, String frozenHash, byte[] file, CieSignVerifier.Result certificate, Signer signer) {
        SignaturePurpose purpose = cieSignPurpose(role);
        Checked checked = check(purpose, actId, frozenHash, signer, true);
        InventoryLoanSheet sheet = sheetRepository.findById(checked.act().getSheetId()).orElseThrow(LoanSheetSignatureService::actNotFound);
        documents.storeSigned(checked.document(), file, sheet, checked.act(), signer.tenantCode(), signer.actor());
        return record(checked, purpose, SignatureEvidenceMethod.CIESIGN, null, certificate, signer);
    }

    /**
     * Records a signature in the evidence chain; once every confirmation of the signer is recorded the signer has
     * signed, and an act whose signers have all signed is completed.
     */
    private ActOutcome record(
        Checked checked,
        SignaturePurpose purpose,
        SignatureEvidenceMethod method,
        SignatureChallenge challenge,
        CieSignVerifier.Result certificate,
        Signer signer
    ) {
        ZonedDateTime now = ZonedDateTime.now();
        evidenceService.append(new SignatureEvidenceService.Draft(
            checked.act().getId(),
            checked.document().getId(),
            checked.signer().getId(),
            purpose,
            method,
            checked.document().getFrozenHash(),
            declaration(purpose, checked.item()),
            signer.displayName(),
            signer.userId(),
            challenge == null ? null : challenge.getDestinationEmail(),
            certificate == null ? null : certificate.subject(),
            certificate == null ? null : certificate.issuer(),
            certificate == null ? null : certificate.serial(),
            certificate == null ? null : certificate.taxCode(),
            certificate == null ? null : certificate.revocationCheck(),
            challenge == null ? null : challenge.getId(),
            signer.sessionId(),
            signer.ipAddress(),
            signer.userAgent()
        ), signer.actor());
        Set<SignaturePurpose> signed = evidenceRepository.findAllByActIdOrderBySequenceAsc(checked.act().getId()).stream()
            .filter(evidence -> checked.signer().getId().equals(evidence.getSignerId()))
            .map(SignatureEvidence::getPurpose)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(SignaturePurpose.class)));
        if (LoanSheetSignatureService.pendingPurposes(checked.act(), checked.signer(), signed).isEmpty()) {
            checked.signer().setStatus(SignerStatus.SIGNED);
            checked.signer().setSignedAt(now);
            checked.signer().touchAudit(signer.actor());
            signerRepository.saveAndFlush(checked.signer());
        }
        metrics.act(checked.act().getActType(), "signature");
        completion.completeIfSigned(checked.act(), signer.tenantCode(), signer.token(), signer.actor());
        return new ActOutcome(checked.act().getId(), checked.act().getActType(), checked.act().getStatus(), null);
    }

    /**
     * What a document signed with CieSign confirms: the acceptance of the act or the approval of the clauses.
     */
    private static SignaturePurpose cieSignPurpose(SignatureDocumentRole role) {
        return switch (role) {
            case MAIN -> SignaturePurpose.ACCEPTANCE;
            case CLAUSES -> SignaturePurpose.CLAUSE_APPROVAL;
            case SNAPSHOT -> throw LoanSheetSignatureService.actNotFound();
        };
    }

    /**
     * The frozen document a CieSign file must start with, and who must have signed it.
     */
    public record CieSignTarget(String frozenHash, byte[] frozen, CieSignVerifier.Identity identity) {
        @Override
        public String toString() {
            return "CieSignTarget[frozenHash=" + frozenHash + "]";
        }
    }

    private record Checked(SignatureAct act, SignatureActSigner signer, SignatureDocument document, Item item) {
    }

    /**
     * Checks that the signer can give the confirmation on the act; {@code documentHash} is the hash shown to the
     * signer, or {@code null} for a CieSign file, which is compared with the frozen document itself.
     */
    private Checked check(SignaturePurpose purpose, Long actId, String documentHash, Signer who, boolean lock) {
        SignatureAct act = signatureService.sheetAct(actId, lock);
        SignerRole role = switch (purpose) {
            case ASSOCIATION_SIGNATURE -> SignerRole.ASSOCIATION;
            case MINOR_ACKNOWLEDGMENT -> SignerRole.MINOR;
            default -> SignerRole.HOLDER;
        };
        SignatureActSigner signer = signatureService.signer(act, role)
            .filter(who::is)
            .orElseThrow(LoanSheetSignatureService::actNotFound);
        if (act.getStatus() != SignatureActStatus.AWAITING_SIGNATURES) {
            throw SignatureSupport.error(HttpStatus.CONFLICT, "The act does not wait for signatures", "signature.act.notAwaitingSignatures");
        }
        if (purpose == SignaturePurpose.CLAUSE_APPROVAL && act.getActType() != SignatureActType.LOAN_SHEET_ISSUE) throw invalid("Only the issue has clauses to approve");
        if (purpose == SignaturePurpose.MINOR_ACKNOWLEDGMENT && act.getActType() != SignatureActType.LOAN_SHEET_ISSUE) throw invalid("The minor acknowledges only the issue");
        SignatureDocumentRole documentRole = purpose == SignaturePurpose.CLAUSE_APPROVAL ? SignatureDocumentRole.CLAUSES : SignatureDocumentRole.MAIN;
        SignatureDocument document = documentRepository.findFirstByActIdAndRoleAndDeletedFalse(act.getId(), documentRole)
            .orElseThrow(() -> SignatureSupport.error(HttpStatus.CONFLICT, "The act does not wait for signatures", "signature.act.notAwaitingSignatures"));
        if (documentHash != null && !MessageDigest.isEqual(document.getFrozenHash().getBytes(StandardCharsets.US_ASCII), documentHash.getBytes(StandardCharsets.US_ASCII))) {
            throw SignatureSupport.error(HttpStatus.CONFLICT, "The document differs from the one shown to the signer", "signature.act.documentChanged");
        }
        boolean already = evidenceRepository.findAllByActIdOrderBySequenceAsc(act.getId()).stream()
            .anyMatch(evidence -> signer.getId().equals(evidence.getSignerId()) && evidence.getPurpose() == purpose);
        if (already) throw SignatureSupport.error(HttpStatus.CONFLICT, "The signer already signed", "signature.act.alreadySigned");
        if (role == SignerRole.ASSOCIATION && !signatureService.activeAdministrator(who.userId())) {
            throw SignatureSupport.error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "The signer for the association is no longer an active administrator",
                "signature.associationSigner.notAdmin"
            );
        }
        String sheetNumber = sheetRepository.findById(act.getSheetId()).map(sheet -> sheet.getSheetNumber()).orElse("");
        Item item = new Item(act.getId(), documentRole, document.getFrozenHash(), act.getActType(), sheetNumber, signatureService.operation(act));
        return new Checked(act, signer, document, item);
    }

    private static Prepared describe(SignaturePurpose purpose, List<Item> items, String itemsJson) {
        Item first = items.get(0);
        String reference = items.size() == 1 ? first.documentHash().substring(0, 8) : SignatureSupport.sha256(itemsJson).substring(0, 8);
        String operation = switch (purpose) {
            case CLAUSE_APPROVAL -> "Approvazione delle clausole del foglio di affidamento n. " + first.sheetNumber();
            case ASSOCIATION_SIGNATURE -> items.size() == 1
                ? "Firma per l'Associazione: " + subject(first)
                : "Firma per l'Associazione di " + items.size() + " variazioni del foglio di affidamento n. " + first.sheetNumber();
            case MINOR_ACKNOWLEDGMENT -> "Presa visione del socio minorenne: " + subject(first);
            default -> items.size() == 1
                ? "Firma: " + subject(first)
                : "Firma di " + items.size() + " variazioni del foglio di affidamento n. " + first.sheetNumber();
        };
        return new Prepared(itemsJson, operation, reference, items.stream().map(item -> declaration(purpose, item)).toList());
    }

    /**
     * Declaration recorded in the evidence: the hash identifies the document the signer read.
     */
    static String declaration(SignaturePurpose purpose, Item item) {
        String document = subject(item) + " (impronta SHA-256 " + item.documentHash() + ")";
        return switch (purpose) {
            case CLAUSE_APPROVAL -> "Ai sensi e per gli effetti degli artt. 1341 e 1342 c.c. approvo specificamente le clausole elencate nel documento "
                + "di approvazione del foglio di affidamento n. " + item.sheetNumber() + " (impronta SHA-256 " + item.documentHash() + ").";
            case ASSOCIATION_SIGNATURE -> "Firmo per l'Associazione " + document + ".";
            case MINOR_ACKNOWLEDGMENT -> "Ho preso visione " + of(subject(item)) + " (impronta SHA-256 " + item.documentHash() + "), che il mio genitore o tutore firma come Affidatario.";
            default -> switch (item.actType()) {
                case LOAN_SHEET_ISSUE -> "Ho letto " + document + " e lo accetto, assumendo gli obblighi dell'Affidatario.";
                case LOAN_SHEET_VARIATION -> "Ho letto " + document + " e la accetto.";
                default -> "Ho letto " + document + " e lo accetto.";
            };
        };
    }

    private static String subject(Item item) {
        return switch (item.actType()) {
            case LOAN_SHEET_ISSUE -> "il foglio di consegna e affidamento in comodato n. " + item.sheetNumber();
            case LOAN_SHEET_VARIATION -> "la variazione del foglio di affidamento n. " + item.sheetNumber() + " «" + item.operation() + "»";
            case LOAN_SHEET_RETURN -> "il verbale di riconsegna del foglio di affidamento n. " + item.sheetNumber();
            case ASSIGNMENT_ACKNOWLEDGMENT -> "la presa visione";
        };
    }

    /**
     * The subject after "di", with the article merged: "del foglio", "della variazione".
     */
    private static String of(String subject) {
        if (subject.startsWith("il ")) return "del " + subject.substring(3);
        if (subject.startsWith("la ")) return "della " + subject.substring(3);
        return "di " + subject;
    }

    private static String json(List<Item> items) {
        try {
            return CANONICAL.writeValueAsString(items);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize the signature items", exception);
        }
    }

    private static List<Item> items(String json) {
        try {
            return CANONICAL.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read the signature items", exception);
        }
    }

    private static RuntimeException invalid(String message) {
        return SignatureSupport.error(HttpStatus.BAD_REQUEST, message, "signature.challenge.invalid");
    }

    /**
     * An act confirmed by a code, with what the signer was shown: its JSON binds the code.
     */
    record Item(Long actId, SignatureDocumentRole documentRole, String documentHash, SignatureActType actType, String sheetNumber, String operation) {
    }
}
