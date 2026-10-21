package com.fundaro.zodiac.taurus.service.signature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentDecision;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import com.fundaro.zodiac.taurus.domain.signature.*;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActSignerRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureDocumentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentDecisionRepository;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryDecisionRequest;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ActOutcome;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ChallengeItem;
import com.fundaro.zodiac.taurus.service.impl.InventoryService;
import com.fundaro.zodiac.taurus.service.impl.InventoryService.DecisionCheck;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Acknowledgment of assignment revisions confirmed by a code: one or more decisions, each recorded with an act, the
 * hash of the revision as its document and the evidence of the signature.
 */
@Component
public class AssignmentDecisionSignatureHandler implements SignaturePurposeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AssignmentDecisionSignatureHandler.class);
    private static final ObjectMapper CANONICAL = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final InventoryService inventoryService;
    private final InventoryAssignmentDecisionRepository decisionRepository;
    private final SignatureActRepository actRepository;
    private final SignatureDocumentRepository documentRepository;
    private final SignatureActSignerRepository signerRepository;
    private final SignatureEvidenceService evidenceService;
    private final SignatureMetrics metrics;
    private final int maxItems;

    public AssignmentDecisionSignatureHandler(
        InventoryService inventoryService,
        InventoryAssignmentDecisionRepository decisionRepository,
        SignatureActRepository actRepository,
        SignatureDocumentRepository documentRepository,
        SignatureActSignerRepository signerRepository,
        SignatureEvidenceService evidenceService,
        SignatureMetrics metrics,
        ApplicationProperties applicationProperties
    ) {
        this.inventoryService = inventoryService;
        this.decisionRepository = decisionRepository;
        this.actRepository = actRepository;
        this.documentRepository = documentRepository;
        this.signerRepository = signerRepository;
        this.evidenceService = evidenceService;
        this.metrics = metrics;
        this.maxItems = applicationProperties.getSignature().getMaxBatchItems();
    }

    @Override
    public SignaturePurpose purpose() {
        return SignaturePurpose.ASSIGNMENT_DECISION;
    }

    @Override
    public Prepared prepare(List<ChallengeItem> items, Signer signer) {
        if (!inventoryService.electronicSignatureEnabled()) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "Electronic signature is disabled in the tenant", "signature.disabled");
        }
        if (items == null || items.isEmpty() || items.size() > maxItems) {
            throw invalid("The acknowledgment needs from 1 to " + maxItems + " assignments");
        }
        Set<Long> assignments = new HashSet<>();
        List<Item> canonical = new ArrayList<>();
        for (ChallengeItem item : items) {
            if (item.assignmentId() == null || item.revisionHash() == null || item.decision() == null) throw invalid("Incomplete acknowledgment item");
            if (!assignments.add(item.assignmentId())) throw invalid("Duplicate assignment in the acknowledgment");
            canonical.add(Item.of(inventoryService.checkDecision(item.assignmentId(), request(item), signer.userId(), false)));
        }
        return describe(canonical, json(canonical));
    }

    @Override
    public Prepared describe(String itemsJson) {
        return describe(items(itemsJson), itemsJson);
    }

    private static Prepared describe(List<Item> items, String itemsJson) {
        String reference = items.size() == 1 ? items.get(0).revisionHash().substring(0, 8) : SignatureSupport.sha256(itemsJson).substring(0, 8);
        String operation = items.size() == 1
            ? "Presa visione dell'assegnazione di «" + items.get(0).itemName() + "»"
            : "Presa visione di " + items.size() + " assegnazioni";
        return new Prepared(itemsJson, operation, reference, items.stream().map(Item::declaration).toList());
    }

    @Override
    public List<ActOutcome> confirm(SignatureChallenge challenge, Signer signer) {
        List<ActOutcome> outcomes = new ArrayList<>();
        for (Item item : items(challenge.getItemsJson())) {
            inventoryService.decideSigned(
                item.assignmentId(),
                new InventoryDecisionRequest(item.decision(), item.rejectionReason(), item.revisionHash()),
                signer.token(),
                (check, decision) -> outcomes.add(sign(challenge, signer, item, check, decision))
            );
        }
        return outcomes;
    }

    /**
     * Records the act, its document and signer and the evidence of a decision just taken.
     */
    private ActOutcome sign(SignatureChallenge challenge, Signer signer, Item item, DecisionCheck check, InventoryAssignmentDecision decision) {
        ZonedDateTime now = ZonedDateTime.now();
        SignatureAct act = new SignatureAct();
        act.initializeAudit(signer.userId());
        act.setActType(SignatureActType.ASSIGNMENT_ACKNOWLEDGMENT);
        act.setStatus(SignatureActStatus.SIGNED);
        act.setAssignmentId(item.assignmentId());
        act.setDecisionId(decision.getId());
        act.setCompletedAt(now);
        act = actRepository.save(act);

        SignatureDocument document = new SignatureDocument();
        document.initializeAudit(signer.userId());
        document.setActId(act.getId());
        document.setRole(SignatureDocumentRole.SNAPSHOT);
        document.setFrozenHash(check.revision().getSnapshotHash());
        document = documentRepository.save(document);

        SignatureActSigner actSigner = new SignatureActSigner();
        actSigner.initializeAudit(signer.userId());
        actSigner.setActId(act.getId());
        actSigner.setSignerRole(SignerRole.ASSIGNEE);
        actSigner.setRequired(true);
        actSigner.setUserKeycloakId(signer.userId());
        actSigner.setDisplayName(signer.displayName());
        actSigner.setStatus(SignerStatus.SIGNED);
        actSigner.setSignedAt(now);
        actSigner = signerRepository.save(actSigner);

        SignatureEvidence evidence = evidenceService.append(new SignatureEvidenceService.Draft(
            act.getId(),
            document.getId(),
            actSigner.getId(),
            SignaturePurpose.ASSIGNMENT_DECISION,
            challenge.getMethod() == SignatureMethod.TOTP ? SignatureEvidenceMethod.TOTP : SignatureEvidenceMethod.EMAIL_OTP,
            check.revision().getSnapshotHash(),
            item.declaration(),
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
        decision.setSignatureEvidenceId(evidence.getId());
        decisionRepository.save(decision);
        metrics.act(SignatureActType.ASSIGNMENT_ACKNOWLEDGMENT, "signed");
        LOG.info("signature_act_signed actId={} type={} evidence={}", act.getId(), act.getActType(), evidence.getSequence());
        return new ActOutcome(act.getId(), act.getActType(), act.getStatus(), item.assignmentId());
    }

    private static InventoryDecisionRequest request(ChallengeItem item) {
        return new InventoryDecisionRequest(item.decision(), item.rejectionReason(), item.revisionHash());
    }

    private static String json(List<Item> items) {
        try {
            return CANONICAL.writeValueAsString(items);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize the acknowledgment items", exception);
        }
    }

    private static List<Item> items(String json) {
        try {
            return CANONICAL.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read the acknowledgment items", exception);
        }
    }

    private static RuntimeException invalid(String message) {
        return SignatureSupport.error(HttpStatus.BAD_REQUEST, message, "signature.challenge.invalid");
    }

    /**
     * A checked decision, with the data of the item printed in the declaration: the revision hash covers them, so the
     * declaration cannot differ from what the user saw.
     */
    record Item(
        Long assignmentId,
        String revisionHash,
        InventoryDecisionType decision,
        String rejectionReason,
        String itemName,
        String inventoryNumber,
        int revisionNumber
    ) {
        static Item of(DecisionCheck check) {
            return new Item(
                check.assignment().getId(),
                check.revision().getSnapshotHash(),
                check.decision(),
                check.rejectionReason(),
                check.assignment().getItem().getName(),
                check.assignment().getItem().getInventoryNumber(),
                check.revision().getRevisionNumber()
            );
        }

        /**
         * Declaration recorded in the evidence, with the name and the inventory number of the item and the revision.
         */
        String declaration() {
            String subject = "Ho preso visione dei dati e delle fotografie dell'assegnazione di «" + itemName
                + "» (numero inventariale " + inventoryNumber + ", revisione " + revisionNumber + ")";
            return decision == InventoryDecisionType.REJECTED
                ? subject + " e li rifiuto per il seguente motivo: " + rejectionReason
                : subject + " e li accetto.";
        }
    }
}
