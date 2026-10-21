package com.fundaro.zodiac.taurus.service.signature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.domain.signature.SignatureAct;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureChallenge;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocument;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidenceMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanSheetRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureDocumentRepository;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ActOutcome;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ChallengeItem;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Declaration by an administrator, confirmed with the own code, that an act was signed on paper: when the parent cannot
 * sign electronically or for technical problems. The paper copy is kept by the association.
 */
@Component
public class PaperDeclarationSignatureHandler implements SignaturePurposeHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final LoanSheetSignatureService signatureService;
    private final LoanSheetActCompletion completion;
    private final SignatureDocumentRepository documentRepository;
    private final SignatureEvidenceService evidenceService;
    private final InventoryLoanSheetRepository sheetRepository;

    public PaperDeclarationSignatureHandler(
        LoanSheetSignatureService signatureService,
        LoanSheetActCompletion completion,
        SignatureDocumentRepository documentRepository,
        SignatureEvidenceService evidenceService,
        InventoryLoanSheetRepository sheetRepository
    ) {
        this.signatureService = signatureService;
        this.completion = completion;
        this.documentRepository = documentRepository;
        this.evidenceService = evidenceService;
        this.sheetRepository = sheetRepository;
    }

    @Override
    public SignaturePurpose purpose() {
        return SignaturePurpose.PAPER_DECLARATION;
    }

    @Override
    public Prepared prepare(List<ChallengeItem> items, Signer signer) {
        if (items == null || items.size() != 1) throw invalid("A declaration concerns one act");
        ChallengeItem item = items.get(0);
        String note = SignatureSupport.trimToNull(item.note());
        if (item.actId() == null || note == null) throw invalid("The declaration needs the act and a note");
        Item checked = check(item.actId(), note, signer.userId(), false);
        return describe(checked, json(checked));
    }

    @Override
    public Prepared describe(String itemsJson) {
        return describe(item(itemsJson), itemsJson);
    }

    @Override
    public List<ActOutcome> confirm(SignatureChallenge challenge, Signer signer) {
        Item item = item(challenge.getItemsJson());
        check(item.actId(), item.note(), signer.userId(), true);
        SignatureAct act = signatureService.sheetAct(item.actId(), false);
        SignatureDocument main = documentRepository.findFirstByActIdAndRoleAndDeletedFalse(act.getId(), SignatureDocumentRole.MAIN).orElse(null);
        evidenceService.append(new SignatureEvidenceService.Draft(
            act.getId(),
            main == null ? null : main.getId(),
            null,
            SignaturePurpose.PAPER_DECLARATION,
            SignatureEvidenceMethod.PAPER_DECLARATION,
            main == null ? null : main.getFrozenHash(),
            declaration(item),
            signer.displayName(),
            signer.userId(),
            challenge.getDestinationEmail(),
            null,
            null,
            null,
            null,
            null,
            challenge.getId(),
            signer.sessionId(),
            signer.ipAddress(),
            signer.userAgent()
        ), signer.userId());
        completion.completeOnPaper(act, item.note(), signer.token(), signer.userId());
        return List.of(new ActOutcome(act.getId(), act.getActType(), act.getStatus(), null));
    }

    private Item check(Long actId, String note, String userId, boolean lock) {
        if (!signatureService.activeAdministrator(userId)) throw LoanSheetSignatureService.actNotFound();
        SignatureAct act = signatureService.sheetAct(actId, lock);
        if (!LoanSheetSignatureService.AWAITING.contains(act.getStatus())) {
            throw SignatureSupport.error(HttpStatus.CONFLICT, "The act does not wait for signatures", "signature.act.notAwaitingSignatures");
        }
        String sheetNumber = sheetRepository.findById(act.getSheetId()).map(sheet -> sheet.getSheetNumber()).orElse("");
        return new Item(act.getId(), note, act.getActType(), sheetNumber, signatureService.operation(act));
    }

    private static Prepared describe(Item item, String itemsJson) {
        return new Prepared(
            itemsJson,
            "Dichiarazione di firma su carta del foglio di affidamento n. " + item.sheetNumber(),
            SignatureSupport.sha256(itemsJson).substring(0, 8),
            List.of(declaration(item))
        );
    }

    static String declaration(Item item) {
        return "Dichiaro che l'atto «" + item.operation() + "» del foglio di affidamento n. " + item.sheetNumber()
            + " è stato firmato su carta dalle parti e che la copia cartacea è custodita dall'Associazione. Nota: " + item.note();
    }

    private static String json(Item item) {
        try {
            return JSON.writeValueAsString(item);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize the declaration", exception);
        }
    }

    private static Item item(String json) {
        try {
            return JSON.readValue(json, Item.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read the declaration", exception);
        }
    }

    private static RuntimeException invalid(String message) {
        return SignatureSupport.error(HttpStatus.BAD_REQUEST, message, "signature.challenge.invalid");
    }

    record Item(Long actId, String note, SignatureActType actType, String sheetNumber, String operation) {
    }
}
