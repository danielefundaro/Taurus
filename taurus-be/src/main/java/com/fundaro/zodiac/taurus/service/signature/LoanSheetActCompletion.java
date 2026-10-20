package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheet;
import com.fundaro.zodiac.taurus.domain.signature.SignatureAct;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActSigner;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActStatus;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocument;
import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidence;
import com.fundaro.zodiac.taurus.domain.signature.SignerRole;
import com.fundaro.zodiac.taurus.domain.signature.SignerStatus;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanSheetRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActSignerRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureDocumentRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureEvidenceRepository;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetReturnSigned;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Completion of an act of a loan sheet: once every required signer has signed, each document gets the seal of the
 * installation, on top of the CieSign signature when the holder used it, and the act gets its sealed attestation. A return report signed, or declared signed on paper, lets the
 * sheet be settled.
 */
@Component
public class LoanSheetActCompletion {

    private static final Logger LOG = LoggerFactory.getLogger(LoanSheetActCompletion.class);

    private final SignatureActRepository actRepository;
    private final SignatureActSignerRepository signerRepository;
    private final SignatureDocumentRepository documentRepository;
    private final SignatureEvidenceRepository evidenceRepository;
    private final InventoryLoanSheetRepository sheetRepository;
    private final LoanSheetActDocuments documents;
    private final LoanSheetSignatureService signatureService;
    private final SignatureSealService sealService;
    private final SignatureAttestationService attestationService;
    private final SignatureLinkService linkService;
    private final TenantsService tenantsService;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final ApplicationEventPublisher events;
    private final SignatureMetrics metrics;

    public LoanSheetActCompletion(
        SignatureActRepository actRepository,
        SignatureActSignerRepository signerRepository,
        SignatureDocumentRepository documentRepository,
        SignatureEvidenceRepository evidenceRepository,
        InventoryLoanSheetRepository sheetRepository,
        LoanSheetActDocuments documents,
        LoanSheetSignatureService signatureService,
        SignatureSealService sealService,
        SignatureAttestationService attestationService,
        SignatureLinkService linkService,
        TenantsService tenantsService,
        TenantTimeZoneService tenantTimeZoneService,
        ApplicationEventPublisher events,
        SignatureMetrics metrics
    ) {
        this.actRepository = actRepository;
        this.signerRepository = signerRepository;
        this.documentRepository = documentRepository;
        this.evidenceRepository = evidenceRepository;
        this.sheetRepository = sheetRepository;
        this.documents = documents;
        this.signatureService = signatureService;
        this.sealService = sealService;
        this.attestationService = attestationService;
        this.linkService = linkService;
        this.tenantsService = tenantsService;
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.events = events;
        this.metrics = metrics;
    }

    /**
     * Completes the act if every required signer has signed; returns {@code null} while signatures are missing. A
     * parent or guardian without an account receives a new link to download the signed documents. {@code token} is
     * {@code null} when the last signature comes from a link.
     */
    public LoanSheetActChange completeIfSigned(SignatureAct act, String tenantCode, AbstractAuthenticationToken token, String actor) {
        List<SignatureActSigner> signers = signerRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId());
        if (signers.stream().anyMatch(signer -> signer.isRequired() && signer.getStatus() != SignerStatus.SIGNED)) return null;
        InventoryLoanSheet sheet = sheetRepository.findById(act.getSheetId())
            .orElseThrow(() -> SignatureSupport.error(HttpStatus.NOT_FOUND, "Loan sheet not found", "inventory.loanSheet.notFound"));
        ZonedDateTime now = ZonedDateTime.now();
        List<SignatureDocument> frozen = documentRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId());
        for (SignatureDocument document : frozen) {
            byte[] sealed = sealService.seal(documents.toSeal(document, tenantCode), "Atto del foglio di affidamento n. " + sheet.getSheetNumber());
            documents.storeSealed(document, sealed, sheet, act, tenantCode, actor);
        }
        act.setCompletedAt(now);
        byte[] attestation = attestationService.create(attestation(act, sheet, frozen, signers, now, tenantCode));
        act.setAttestationMediaAssetId(documents.storeAttestation(attestation, sheet, act, tenantCode, actor));
        act.setStatus(SignatureActStatus.SIGNED);
        act.touchAudit(actor);
        actRepository.saveAndFlush(act);
        metrics.act(act.getActType(), "signed");
        LOG.info("signature_act_signed actId={} type={} sheetId={}", act.getId(), act.getActType(), sheet.getId());
        for (SignatureActSigner signer : signers) {
            if (signer.getSignerRole() == SignerRole.HOLDER && signer.getUserKeycloakId() == null && signer.getEmail() != null) {
                linkService.createAndSendAfterCommit(signer, signatureService.linkContent(act, sheet), true, actor);
            }
        }
        returnSigned(act, sheet, tenantCode, actor);
        return signatureService.change(sheet, act, holders(signers), null);
    }

    /**
     * Closes an act declared signed on paper: the paper copy is kept by the association, the frozen files are removed.
     */
    public LoanSheetActChange completeOnPaper(SignatureAct act, String note, AbstractAuthenticationToken token, String actor) {
        String tenant = SignatureSupport.tenant(token);
        InventoryLoanSheet sheet = sheetRepository.findById(act.getSheetId())
            .orElseThrow(() -> SignatureSupport.error(HttpStatus.NOT_FOUND, "Loan sheet not found", "inventory.loanSheet.notFound"));
        ZonedDateTime now = ZonedDateTime.now();
        List<SignatureActSigner> signers = signerRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId());
        for (SignatureActSigner signer : signers) {
            if (signer.getStatus() != SignerStatus.PENDING) continue;
            signer.setStatus(SignerStatus.SIGNED_ON_PAPER);
            signer.setSignedAt(now);
            signer.touchAudit(actor);
            signerRepository.save(signer);
        }
        for (SignatureDocument document : documentRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId())) {
            documents.removeFiles(document, tenant, actor);
        }
        linkService.revoke(signers, actor);
        act.setStatus(SignatureActStatus.SIGNED_ON_PAPER);
        act.setPaperNote(note);
        act.setCompletedAt(now);
        act.touchAudit(actor);
        actRepository.saveAndFlush(act);
        metrics.act(act.getActType(), "signedOnPaper");
        LOG.info("signature_act_signed_on_paper actId={} type={} sheetId={}", act.getId(), act.getActType(), sheet.getId());
        returnSigned(act, sheet, tenant, actor);
        return signatureService.change(sheet, act, holders(signers), null);
    }

    private void returnSigned(SignatureAct act, InventoryLoanSheet sheet, String tenantCode, String actor) {
        if (act.getActType() == SignatureActType.LOAN_SHEET_RETURN) {
            events.publishEvent(new LoanSheetReturnSigned(sheet.getId(), tenantCode, actor));
        }
    }

    private SignatureAttestationService.Attestation attestation(
        SignatureAct act,
        InventoryLoanSheet sheet,
        List<SignatureDocument> frozen,
        List<SignatureActSigner> signers,
        ZonedDateTime completedAt,
        String tenant
    ) {
        String tenantName = tenantsService.findByCode(tenant, null).map(TenantsDTO::getName).filter(name -> !name.isBlank()).orElse(tenant);
        Map<Long, SignatureActSigner> signerById = signers.stream().collect(Collectors.toMap(SignatureActSigner::getId, Function.identity()));
        Map<Long, SignatureDocument> documentById = frozen.stream().collect(Collectors.toMap(SignatureDocument::getId, Function.identity()));
        List<SignatureAttestationService.Document> documentRows = documentRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId()).stream()
            .map(document -> new SignatureAttestationService.Document(document.getRole(), document.getFrozenHash(), document.getSealedHash()))
            .toList();
        List<SignatureAttestationService.Signature> signatures = evidenceRepository.findAllByActIdOrderBySequenceAsc(act.getId()).stream()
            .map(evidence -> signature(evidence, signerById, documentById))
            .toList();
        return new SignatureAttestationService.Attestation(
            tenantName,
            act.getActType(),
            sheet.getSheetNumber(),
            act.getId(),
            completedAt,
            tenantTimeZoneService.currentZoneId(),
            documentRows,
            signatures
        );
    }

    private static SignatureAttestationService.Signature signature(
        SignatureEvidence evidence,
        Map<Long, SignatureActSigner> signers,
        Map<Long, SignatureDocument> documents
    ) {
        return new SignatureAttestationService.Signature(
            evidence.getSignerName(),
            Optional.ofNullable(evidence.getSignerId()).map(signers::get).map(SignatureActSigner::getSignerRole).orElse(SignerRole.ASSOCIATION),
            evidence.getPurpose(),
            Optional.ofNullable(evidence.getDocumentId()).map(documents::get).map(SignatureDocument::getRole).orElse(null),
            evidence.getDeclarationText(),
            evidence.getMethod(),
            evidence.getSignedAt(),
            evidence.getDestinationEmail(),
            evidence.getCertificateSubject(),
            evidence.getCertificateIssuer(),
            evidence.getCertificateSerial(),
            evidence.getCertificateTaxCode(),
            evidence.getRevocationCheck(),
            evidence.getIpAddress(),
            evidence.getUserAgent(),
            evidence.getSequence(),
            evidence.getEvidenceHash()
        );
    }

    private static List<String> holders(List<SignatureActSigner> signers) {
        return signers.stream().filter(signer -> signer.getSignerRole() == SignerRole.HOLDER).map(SignatureActSigner::getUserKeycloakId).toList();
    }
}
