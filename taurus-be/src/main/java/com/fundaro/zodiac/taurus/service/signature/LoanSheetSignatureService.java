package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanHolderRole;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheet;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheetStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSignatureMode;
import com.fundaro.zodiac.taurus.domain.signature.SignatureAct;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActSigner;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActStatus;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocument;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidence;
import com.fundaro.zodiac.taurus.domain.signature.SignatureLink;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.domain.signature.SignerRole;
import com.fundaro.zodiac.taurus.domain.signature.SignerStatus;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRenewalRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanSettingsRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryLoanSheetRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActSignerRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureDocumentRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureEvidenceRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureLinkRepository;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.Act;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ActDocument;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ActFile;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ActSigner;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.AssociationSignerChange;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.AssociationSignerOption;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.Evidence;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.HolderDataRequest;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.IssueOptions;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.LinkAct;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.LinkView;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetActs;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.HolderData;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContentFactory;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetText;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetVariationRecorded;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acts of a loan sheet signed electronically: issue, variations of the register and return report, from their creation
 * to the removal at the end of the grace period. The signatures themselves are recorded by
 * {@link LoanSheetSigning}.
 */
@Service
@Transactional
public class LoanSheetSignatureService {

    private static final Logger LOG = LoggerFactory.getLogger(LoanSheetSignatureService.class);
    static final Set<SignatureActType> SHEET_ACTS = EnumSet.of(
        SignatureActType.LOAN_SHEET_ISSUE,
        SignatureActType.LOAN_SHEET_VARIATION,
        SignatureActType.LOAN_SHEET_RETURN
    );
    static final Set<SignatureActStatus> AWAITING = EnumSet.of(SignatureActStatus.AWAITING_DATA, SignatureActStatus.AWAITING_SIGNATURES);
    private static final List<InventoryAssignmentStatus> OUTSTANDING_ASSIGNMENTS = List.of(InventoryAssignmentStatus.ACTIVE, InventoryAssignmentStatus.PARTIALLY_RETURNED);
    static final List<RoleEnum> ADMIN_ROLES = List.of(RoleEnum.ROLE_ADMIN, RoleEnum.ROLE_SUPER_ADMIN);
    static final String REMOVED_NAME = SignatureErasureService.REMOVED_NAME;
    private static final Pattern POSTAL_CODE = Pattern.compile("^\\d{5}$");
    private static final java.time.format.DateTimeFormatter DATE = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SignatureActRepository actRepository;
    private final SignatureActSignerRepository signerRepository;
    private final SignatureDocumentRepository documentRepository;
    private final SignatureEvidenceRepository evidenceRepository;
    private final SignatureEvidenceService evidenceService;
    private final InventoryLoanSheetRepository sheetRepository;
    private final InventoryLoanSettingsRepository settingsRepository;
    private final InventoryAssignmentRepository assignmentRepository;
    private final InventoryAssignmentRenewalRepository renewalRepository;
    private final InventoryReturnRepository returnRepository;
    private final UsersRepository usersRepository;
    private final LoanSheetContentFactory contentFactory;
    private final LoanSheetActDocuments documents;
    private final SignatureLinkService linkService;
    private final SignatureLinkRepository linkRepository;
    private final TenantsService tenantsService;
    private final CieSignVerifier cieSignVerifier;
    private final SignatureMetrics metrics;
    private final int graceDays;

    public LoanSheetSignatureService(
        SignatureActRepository actRepository,
        SignatureActSignerRepository signerRepository,
        SignatureDocumentRepository documentRepository,
        SignatureEvidenceRepository evidenceRepository,
        SignatureEvidenceService evidenceService,
        InventoryLoanSheetRepository sheetRepository,
        InventoryLoanSettingsRepository settingsRepository,
        InventoryAssignmentRepository assignmentRepository,
        InventoryAssignmentRenewalRepository renewalRepository,
        InventoryReturnRepository returnRepository,
        UsersRepository usersRepository,
        LoanSheetContentFactory contentFactory,
        LoanSheetActDocuments documents,
        SignatureLinkService linkService,
        SignatureLinkRepository linkRepository,
        TenantsService tenantsService,
        CieSignVerifier cieSignVerifier,
        SignatureMetrics metrics,
        ApplicationProperties applicationProperties
    ) {
        this.actRepository = actRepository;
        this.signerRepository = signerRepository;
        this.documentRepository = documentRepository;
        this.evidenceRepository = evidenceRepository;
        this.evidenceService = evidenceService;
        this.sheetRepository = sheetRepository;
        this.settingsRepository = settingsRepository;
        this.assignmentRepository = assignmentRepository;
        this.renewalRepository = renewalRepository;
        this.returnRepository = returnRepository;
        this.usersRepository = usersRepository;
        this.contentFactory = contentFactory;
        this.documents = documents;
        this.linkService = linkService;
        this.linkRepository = linkRepository;
        this.tenantsService = tenantsService;
        this.cieSignVerifier = cieSignVerifier;
        this.metrics = metrics;
        this.graceDays = applicationProperties.getSignature().getDownloadGraceDays();
    }

    /**
     * Who signs for the association: an active administrator, never the holder of the sheet.
     */
    public record AssociationSigner(String userId, String displayName) {
    }

    /**
     * A link expired without the signature of the parent, notified to the administrators.
     */
    public record ExpiredLink(Long actId, String sheetNumber, String operation, String signerName) {
    }

    @Transactional(readOnly = true)
    public boolean enabled() {
        return settingsRepository.findFirstByDeletedFalse().map(settings -> settings.isElectronicSignatureEnabled()).orElse(false);
    }

    /**
     * Signature options of the issue of a sheet: the current administrator signs for the association by default.
     */
    @Transactional(readOnly = true)
    public IssueOptions issueOptions(String holderUserId, String template, String actor) {
        List<AssociationSignerOption> signers = associationSigners(holderUserId);
        String preferred = signers.stream().map(AssociationSignerOption::userId).filter(actor::equals).findFirst().orElse(null);
        return new IssueOptions(enabled(), LoanSheetActs.signable(template), signers, preferred);
    }

    /**
     * Active administrators who can sign for the association on a sheet of the holder.
     */
    @Transactional(readOnly = true)
    public List<AssociationSignerOption> associationSigners(String holderUserId) {
        List<AssociationSignerOption> options = new ArrayList<>();
        for (String userId : new LinkedHashSet<>(usersRepository.findActiveKeycloakIdsByRolesIn(ADMIN_ROLES))) {
            if (userId.equals(holderUserId)) continue;
            usersRepository.findByKeycloakIdAndDeletedFalse(userId).ifPresent(user -> options.add(new AssociationSignerOption(userId, displayName(user))));
        }
        options.sort((first, second) -> first.displayName().compareToIgnoreCase(second.displayName()));
        return options;
    }

    @Transactional(readOnly = true)
    public List<AssociationSignerOption> associationSignersForMember(Long userIndex) {
        String holder = usersRepository.findByIdAndDeletedFalse(userIndex).map(Users::getKeycloakId)
            .orElseThrow(() -> SignatureSupport.error(HttpStatus.NOT_FOUND, "User not found in the tenant", "inventory.user.notFound"));
        return associationSigners(holder);
    }

    /**
     * Checks the issue of a sheet signed electronically and returns who signs for the association. {@code holderEmail}
     * is the address of the adult member or, for a parent or guardian, the one entered by the administrator.
     */
    @Transactional(readOnly = true)
    public AssociationSigner checkIssue(InventoryLoanHolderRole role, String holderUserId, String holderEmail, String associationSignerId, String template) {
        if (!enabled()) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "Electronic signature is disabled in the tenant", "inventory.loanSheet.signatureDisabled");
        }
        if (holderEmail == null || holderEmail.isBlank()) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "The holder has no e-mail address", "inventory.loanSheet.holderEmailRequired");
        }
        if (!LoanSheetActs.signable(template)) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "The template in use has no valid signature markers", "inventory.loanSheet.templateNotSignable");
        }
        return associationSigner(associationSignerId, holderUserId);
    }

    /**
     * Creates the issue act of a sheet signed electronically: it waits for the data the holder enters instead of
     * filling in the paper form. A parent or guardian receives the link to the public page; the minor member
     * acknowledges the sheet from the own account once the documents are ready.
     */
    public LoanSheetActChange createIssueAct(InventoryLoanSheet sheet, AssociationSigner associationSigner, String actor) {
        SignatureAct act = newAct(SignatureActType.LOAN_SHEET_ISSUE, SignatureActStatus.AWAITING_DATA, sheet, actor);
        SignatureActSigner holder = addHolder(act, sheet, actor);
        addSigner(act, SignerRole.ASSOCIATION, associationSigner.userId(), associationSigner.displayName(), null, true, actor);
        if (guardian(sheet)) addSigner(act, SignerRole.MINOR, sheet.getUserKeycloakId(), memberName(sheet), null, false, actor);
        created(act, sheet);
        sendLink(act, sheet, holder, actor);
        return change(sheet, act, Arrays.asList(holder.getUserKeycloakId()), null);
    }

    /**
     * Records the data of the holder and freezes the documents of the issue. Until somebody signs, the holder can
     * correct the data: new documents replace the previous ones.
     */
    public Act submitHolderData(long actId, HolderDataRequest request, AbstractAuthenticationToken token) {
        String tenant = SignatureSupport.tenant(token);
        String userId = SignatureSupport.actor(token);
        SignatureAct act = sheetAct(actId, true);
        SignatureActSigner holder = signer(act, SignerRole.HOLDER)
            .filter(signer -> userId.equals(signer.getUserKeycloakId()))
            .orElseThrow(LoanSheetSignatureService::actNotFound);
        recordHolderData(act, holder, request, tenant, userId);
        return view(act, userId);
    }

    /**
     * Records the data entered by a parent or guardian through the signature link, with the own name and date of birth.
     * Returns the full view of the act, for the notices to the other signers.
     */
    public Act submitLinkHolderData(SignatureLinkSession session, HolderDataRequest request) {
        linkService.requireActive(session);
        SignatureAct act = sheetAct(session.actId(), true);
        SignatureActSigner holder = signer(act, SignerRole.HOLDER)
            .filter(signer -> signer.getId().equals(session.signerId()) && signer.getUserKeycloakId() == null)
            .orElseThrow(LoanSheetSignatureService::actNotFound);
        recordHolderData(act, holder, request, session.tenantCode(), session.actor());
        return view(act, (String) null);
    }

    private void recordHolderData(SignatureAct act, SignatureActSigner holder, HolderDataRequest request, String tenant, String actor) {
        if (act.getActType() != SignatureActType.LOAN_SHEET_ISSUE || !AWAITING.contains(act.getStatus()) || evidenceRepository.existsByActId(act.getId())) {
            throw SignatureSupport.error(HttpStatus.CONFLICT, "The act does not wait for the data of the holder", "signature.act.notAwaitingData");
        }
        InventoryLoanSheet sheet = sheetRepository.findForUpdate(act.getSheetId()).orElseThrow(LoanSheetSignatureService::actNotFound);
        if (sheet.getStatus() != InventoryLoanSheetStatus.ISSUED) {
            throw SignatureSupport.error(HttpStatus.CONFLICT, "The sheet is no longer issued", "signature.act.notAwaitingData");
        }
        // The date of birth of the member comes from the user profile, as name and surname: the documents need it.
        LocalDate birthDate = memberBirthDate(sheet);
        if (birthDate == null) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "The date of birth of the member is missing in the user profile", "signature.holderData.birthDateMissing")
                .with("holderRole", sheet.getHolderRole().name());
        }
        requireNoPendingAcknowledgments(sheet.getId());
        HolderData data = holderData(request, sheet.getHolderRole(), sheet.getHolderEmail());
        sheet.setMemberBirthDate(birthDate);
        documents.discard(documentRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId()), tenant, actor);
        documents.freeze(act, sheet, data, tenant, actor);
        sheet.setHolderTaxCode(data.taxCode());
        sheet.setHolderNoTaxCode(data.taxCode() == null);
        if (data.holderName() != null) {
            sheet.setHolderName(data.holderName());
            holder.setDisplayName(data.holderName());
            holder.touchAudit(actor);
            signerRepository.save(holder);
        }
        sheet.touchAudit(actor);
        sheetRepository.save(sheet);
        act.setStatus(SignatureActStatus.AWAITING_SIGNATURES);
        act.touchAudit(actor);
        actRepository.saveAndFlush(act);
        LOG.info("signature_holder_data_recorded actId={}", act.getId());
    }

    /**
     * Creates the act of a variation of a sheet signed electronically, with the rows of the operation. A return that
     * leaves nothing outstanding is documented by the return report.
     */
    public LoanSheetActChange createVariationAct(LoanSheetVariationRecorded event) {
        InventoryLoanSheet sheet = sheetRepository.findByIdAndDeletedFalse(event.sheetId()).orElse(null);
        if (sheet == null || sheet.getSignatureMode() != InventoryLoanSignatureMode.ELECTRONIC || sheet.getStatus() != InventoryLoanSheetStatus.ISSUED) return null;
        if (event.returnId() != null && nothingOutstanding(sheet)) return null;
        if (!contentFactory.register(sheet).keys().contains(event.registerKey())) {
            LOG.warn("signature_variation_without_row sheetId={} key={}", sheet.getId(), event.registerKey());
            return null;
        }
        String actor = SignatureSupport.actor(event.token());
        SignatureAct act = newAct(SignatureActType.LOAN_SHEET_VARIATION, SignatureActStatus.AWAITING_SIGNATURES, sheet, actor);
        act.setAssignmentId(event.assignmentId());
        act.setRenewalId(event.renewalId());
        act.setReturnId(event.returnId());
        actRepository.save(act);
        AssociationSigner association = operationSigner(sheet, actor);
        SignatureActSigner holder = addHolder(act, sheet, actor);
        addSigner(act, SignerRole.ASSOCIATION, association.userId(), association.displayName(), null, true, actor);
        documents.freeze(act, sheet, null, SignatureSupport.tenant(event.token()), actor);
        created(act, sheet);
        sendLink(act, sheet, holder, actor);
        return change(sheet, act, Arrays.asList(holder.getUserKeycloakId(), association.userId()), null);
    }

    /**
     * Creates the act of the return report of a sheet signed electronically, when the sheet is closed.
     */
    public LoanSheetActChange createReturnAct(InventoryLoanSheet sheet, AbstractAuthenticationToken token) {
        if (sheet.getSignatureMode() != InventoryLoanSignatureMode.ELECTRONIC) return null;
        String actor = SignatureSupport.actor(token);
        SignatureAct act = newAct(SignatureActType.LOAN_SHEET_RETURN, SignatureActStatus.AWAITING_SIGNATURES, sheet, actor);
        AssociationSigner association = operationSigner(sheet, actor);
        SignatureActSigner holder = addHolder(act, sheet, actor);
        addSigner(act, SignerRole.ASSOCIATION, association.userId(), association.displayName(), null, true, actor);
        documents.freeze(act, sheet, null, SignatureSupport.tenant(token), actor);
        created(act, sheet);
        sendLink(act, sheet, holder, actor);
        return change(sheet, act, Arrays.asList(holder.getUserKeycloakId(), association.userId()), null);
    }

    /**
     * Designates another signer for the association while nobody has signed the act. The name is printed in the issue:
     * its documents are discarded and the holder enters the data again, because Taurus keeps them only in the
     * documents.
     */
    public Act changeAssociationSigner(long actId, AssociationSignerChange request, AbstractAuthenticationToken token) {
        String tenant = SignatureSupport.tenant(token);
        String actor = SignatureSupport.actor(token);
        SignatureAct act = sheetAct(actId, true);
        if (act.getEntityVersion() != request.entityVersion()) {
            throw SignatureSupport.error(HttpStatus.CONFLICT, "The act was changed by another request", "signature.act.versionConflict");
        }
        if (!AWAITING.contains(act.getStatus())) {
            throw SignatureSupport.error(HttpStatus.CONFLICT, "The act no longer waits for signatures", "signature.act.notAwaitingSignatures");
        }
        if (evidenceRepository.existsByActId(act.getId())) {
            throw SignatureSupport.error(HttpStatus.CONFLICT, "Somebody already signed the act", "signature.associationSigner.locked");
        }
        InventoryLoanSheet sheet = sheetRepository.findForUpdate(act.getSheetId()).orElseThrow(LoanSheetSignatureService::actNotFound);
        AssociationSigner next = associationSigner(request.userId(), sheet.getUserKeycloakId());
        SignatureActSigner signer = signer(act, SignerRole.ASSOCIATION).orElseThrow(LoanSheetSignatureService::actNotFound);
        signer.setUserKeycloakId(next.userId());
        signer.setDisplayName(next.displayName());
        signer.touchAudit(actor);
        signerRepository.save(signer);
        if (act.getActType() == SignatureActType.LOAN_SHEET_ISSUE) {
            sheet.setAssociationSignatoryName(next.displayName());
            sheet.touchAudit(actor);
            sheetRepository.save(sheet);
            List<SignatureDocument> frozen = documentRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId());
            if (!frozen.isEmpty()) {
                documents.discard(frozen, tenant, actor);
                act.setStatus(SignatureActStatus.AWAITING_DATA);
                // A parent or guardian enters the data again from a new link.
                signer(act, SignerRole.HOLDER).ifPresent(holder -> sendLink(act, sheet, holder, actor));
            }
        }
        act.touchAudit(actor);
        actRepository.saveAndFlush(act);
        LOG.info("signature_association_signer_changed actId={}", act.getId());
        return view(act, actor);
    }

    /**
     * Settlement or cancellation of a sheet: the acts still waiting are cancelled, the signed ones stay downloadable for
     * the grace period, the holder data kept on the sheet are removed.
     */
    public LoanSheetActChange sheetEnded(InventoryLoanSheet sheet, String tenantCode, String actor) {
        ZonedDateTime purgeAfter = ZonedDateTime.now().plusDays(graceDays);
        boolean signed = false;
        for (SignatureAct act : actRepository.findAllBySheetIdAndDeletedFalseOrderByIdAsc(sheet.getId())) {
            if (AWAITING.contains(act.getStatus())) {
                cancel(act, tenantCode, actor);
            } else if (act.getStatus() == SignatureActStatus.SIGNED || act.getStatus() == SignatureActStatus.SIGNED_ON_PAPER) {
                if (act.getPurgedAt() == null && act.getPurgeAfter() == null) {
                    act.setPurgeAfter(purgeAfter);
                    act.touchAudit(actor);
                    actRepository.save(act);
                }
                signed = true;
            }
        }
        clearHolderData(sheet, actor);
        if (!signed) return null;
        LOG.info("signature_sheet_acts_scheduled_for_removal sheetId={} purgeAfter={}", sheet.getId(), purgeAfter);
        return change(sheet, null, List.of(), purgeAfter);
    }

    /**
     * GDPR erasure of the holder of a sheet: acts still waiting are cancelled and documents, attestations and personal
     * data of the evidence of every act are removed at once.
     */
    public void purgeSheet(InventoryLoanSheet sheet, String tenantCode, String actor) {
        for (SignatureAct act : actRepository.findAllBySheetIdAndDeletedFalseOrderByIdAsc(sheet.getId())) {
            if (AWAITING.contains(act.getStatus())) cancel(act, tenantCode, actor);
            if (act.getPurgedAt() == null) purge(act, tenantCode, actor, "gdpr");
        }
        clearHolderData(sheet, actor);
    }

    /**
     * Removes the acts whose grace period has elapsed; returns how many.
     */
    public int purgeDue(ZonedDateTime now, String tenantCode, String actor) {
        List<SignatureAct> due = actRepository.findDueForPurge(now);
        due.forEach(act -> purge(act, tenantCode, actor, "grace"));
        return due.size();
    }

    /**
     * Removes the files and the personal data of a signed act, keeping hashes and chain: the tenant has the dossier.
     */
    void purge(SignatureAct act, String tenantCode, String actor, String reason) {
        ZonedDateTime now = ZonedDateTime.now();
        for (SignatureDocument document : documentRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId())) {
            documents.removeFiles(document, tenantCode, actor);
        }
        Long attestation = act.getAttestationMediaAssetId();
        act.setAttestationMediaAssetId(null);
        act.setPaperNote(null);
        act.setPurgedAt(now);
        act.touchAudit(actor);
        actRepository.saveAndFlush(act);
        documents.deleteMedia(attestation, tenantCode, actor);
        evidenceService.purge(evidenceRepository.findAllByActIdAndPurgedAtIsNull(act.getId()), now, actor);
        List<SignatureActSigner> signers = signerRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId());
        linkService.purge(signers, actor);
        for (SignatureActSigner signer : signers) {
            signer.setUserKeycloakId(null);
            signer.setDisplayName(REMOVED_NAME);
            signer.setEmail(null);
            signer.touchAudit(actor);
            signerRepository.save(signer);
        }
        metrics.purge(reason, "done");
        LOG.info("signature_act_purged actId={} reason={}", act.getId(), reason);
    }

    // Queries and downloads

    @Transactional(readOnly = true)
    public List<Act> sheetActs(long sheetId, AbstractAuthenticationToken token) {
        SignatureSupport.tenant(token);
        String actor = SignatureSupport.actor(token);
        sheetRepository.findByIdAndDeletedFalse(sheetId).orElseThrow(
            () -> SignatureSupport.error(HttpStatus.NOT_FOUND, "Loan sheet not found", "inventory.loanSheet.notFound")
        );
        return actRepository.findAllBySheetIdAndDeletedFalseOrderByIdAsc(sheetId).stream().map(act -> view(act, actor)).toList();
    }

    /**
     * An act of a loan sheet, as shown to the administrators.
     */
    @Transactional(readOnly = true)
    public Act act(long actId, AbstractAuthenticationToken token) {
        SignatureSupport.tenant(token);
        return view(sheetAct(actId, false), SignatureSupport.actor(token));
    }

    /**
     * Acts of loan sheets in which the authenticated user signs, still in Taurus, most recent first.
     */
    @Transactional(readOnly = true)
    public List<Act> ownActs(AbstractAuthenticationToken token) {
        SignatureSupport.tenant(token);
        String userId = SignatureSupport.actor(token);
        return actRepository.findAllForSigner(userId, SHEET_ACTS).stream()
            .filter(act -> act.getPurgedAt() == null && act.getStatus() != SignatureActStatus.CANCELLED)
            .map(act -> view(act, userId))
            .toList();
    }

    @Transactional(readOnly = true)
    public Act ownAct(long actId, AbstractAuthenticationToken token) {
        SignatureSupport.tenant(token);
        String userId = SignatureSupport.actor(token);
        return view(ownedAct(actId, userId), userId);
    }

    @Transactional(readOnly = true)
    public List<Evidence> evidence(long actId, AbstractAuthenticationToken token) {
        SignatureSupport.tenant(token);
        SignatureAct act = sheetAct(actId, false);
        Map<Long, SignatureActSigner> signers = signerRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId()).stream()
            .collect(Collectors.toMap(SignatureActSigner::getId, Function.identity()));
        Map<Long, SignatureDocument> byId = documentRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId()).stream()
            .collect(Collectors.toMap(SignatureDocument::getId, Function.identity()));
        return evidenceRepository.findAllByActIdOrderBySequenceAsc(act.getId()).stream().map(evidence -> new Evidence(
            evidence.getSequence(),
            evidence.getPurpose(),
            evidence.getMethod(),
            Optional.ofNullable(evidence.getSignerId()).map(signers::get).map(SignatureActSigner::getSignerRole).orElse(null),
            evidence.getSignerName(),
            SignatureSupport.maskEmail(evidence.getDestinationEmail()),
            evidence.getDeclarationText(),
            Optional.ofNullable(evidence.getDocumentId()).map(byId::get).map(SignatureDocument::getRole).orElse(null),
            evidence.getDocumentHash(),
            evidence.getSignedAt(),
            evidence.getIpAddress(),
            evidence.getUserAgent(),
            evidence.getCertificateSubject(),
            evidence.getCertificateIssuer(),
            evidence.getCertificateSerial(),
            evidence.getCertificateTaxCode(),
            evidence.getRevocationCheck(),
            evidence.getEvidenceHash(),
            evidence.getPurgedAt() != null
        )).toList();
    }

    /**
     * A document of an act: the sealed copy once the act is signed, otherwise the frozen document shown to the signers.
     */
    @Transactional(readOnly = true)
    public ActFile document(long actId, SignatureDocumentRole role, boolean administrator, AbstractAuthenticationToken token) {
        String tenant = SignatureSupport.tenant(token);
        String userId = SignatureSupport.actor(token);
        SignatureAct act = administrator ? sheetAct(actId, false) : ownedAct(actId, userId);
        LOG.info("signature_document_downloaded actId={} role={} administrator={}", act.getId(), role, administrator);
        return documentFile(act, role, tenant);
    }

    @Transactional(readOnly = true)
    public ActFile attestation(long actId, boolean administrator, AbstractAuthenticationToken token) {
        String tenant = SignatureSupport.tenant(token);
        String userId = SignatureSupport.actor(token);
        SignatureAct act = administrator ? sheetAct(actId, false) : ownedAct(actId, userId);
        LOG.info("signature_attestation_downloaded actId={} administrator={}", act.getId(), administrator);
        return attestationFile(act, tenant);
    }

    // Signature links of the parents without an account

    /**
     * The act of a signature link, as the public page shows it.
     */
    @Transactional(readOnly = true)
    public LinkAct linkAct(SignatureLinkSession session) {
        SignatureAct act = sheetAct(session.actId(), false);
        InventoryLoanSheet sheet = sheetRepository.findById(act.getSheetId()).orElseThrow(LoanSheetSignatureService::actNotFound);
        SignatureActSigner holder = signerRepository.findById(session.signerId()).orElseThrow(LoanSheetSignatureService::actNotFound);
        Act full = view(act, signer -> signer.getId().equals(session.signerId()), false);
        List<ActSigner> signers = full.signers().stream()
            .map(signer -> new ActSigner(signer.id(), signer.role(), null, signer.displayName(), signer.required(), signer.status(), signer.signedAt(), signer.signedPurposes(), null, null))
            .toList();
        Act shown = new Act(
            full.id(),
            full.type(),
            full.status(),
            null,
            full.sheetNumber(),
            full.holderRole(),
            full.operation(),
            full.createdAt(),
            full.completedAt(),
            full.purgeAfter(),
            full.purgedAt(),
            null,
            full.documents(),
            signers,
            full.attestationAvailable(),
            false,
            full.dataRequired(),
            full.memberBirthDate(),
            full.pendingAcknowledgments(),
            full.pendingPurposes(),
            full.cieSignAvailable(),
            full.entityVersion()
        );
        String tenantName = tenantsService.findByCode(session.tenantCode(), null).map(TenantsDTO::getName).filter(name -> !name.isBlank()).orElse(session.tenantCode());
        return new LinkAct(tenantName, memberName(sheet), SignatureSupport.maskEmail(holder.getEmail()), session.expiresAt(), shown);
    }

    @Transactional(readOnly = true)
    public ActFile linkDocument(SignatureLinkSession session, SignatureDocumentRole role) {
        SignatureAct act = sheetAct(session.actId(), false);
        LOG.info("signature_document_downloaded actId={} role={} link=true", act.getId(), role);
        return documentFile(act, role, session.tenantCode());
    }

    @Transactional(readOnly = true)
    public ActFile linkAttestation(SignatureLinkSession session) {
        SignatureAct act = sheetAct(session.actId(), false);
        LOG.info("signature_attestation_downloaded actId={} link=true", act.getId());
        return attestationFile(act, session.tenantCode());
    }

    /**
     * Sends a new link to a parent or guardian, revoking the previous one: to sign an act still waiting or to download
     * the documents of a signed one. The e-mail leaves after the commit, from the caller.
     */
    public SignatureLinkService.PendingLink resendLink(long actId, long signerId, AbstractAuthenticationToken token) {
        SignatureSupport.tenant(token);
        String actor = SignatureSupport.actor(token);
        SignatureAct act = sheetAct(actId, true);
        SignatureActSigner signer = linkSigner(act, signerId);
        boolean signed = act.getStatus() == SignatureActStatus.SIGNED;
        if (signed && act.getPurgedAt() != null) throw purged();
        if (!signed && !AWAITING.contains(act.getStatus())) {
            throw SignatureSupport.error(HttpStatus.CONFLICT, "The act no longer waits for signatures", "signature.act.notAwaitingSignatures");
        }
        if (!signed && signer.getStatus() != SignerStatus.PENDING) {
            throw SignatureSupport.error(HttpStatus.CONFLICT, "The signer already signed", "signature.act.alreadySigned");
        }
        InventoryLoanSheet sheet = sheetRepository.findById(act.getSheetId()).orElseThrow(LoanSheetSignatureService::actNotFound);
        LOG.info("signature_link_resent actId={} signerId={}", act.getId(), signer.getId());
        return linkService.create(signer, linkContent(act, sheet), signed, actor);
    }

    /**
     * Revokes the link of a parent or guardian: the page no longer opens until a new link is sent.
     */
    public Act revokeLink(long actId, long signerId, AbstractAuthenticationToken token) {
        SignatureSupport.tenant(token);
        String actor = SignatureSupport.actor(token);
        SignatureAct act = sheetAct(actId, true);
        linkService.revoke(List.of(linkSigner(act, signerId)), actor);
        return view(act, actor);
    }

    /**
     * Marks the links expired without the signature of the parent while the act waits, and returns them for the notice
     * to the administrators.
     */
    public List<ExpiredLink> expiredLinks(ZonedDateTime now, String actor) {
        List<ExpiredLink> expired = new ArrayList<>();
        for (SignatureLink link : linkRepository.findExpiredUnsigned(now, SignerStatus.PENDING, AWAITING)) {
            link.setExpiryNotifiedAt(now);
            link.touchAudit(actor);
            linkRepository.save(link);
            SignatureActSigner signer = signerRepository.findById(link.getSignerId()).orElse(null);
            SignatureAct act = signer == null ? null : actRepository.findByIdAndDeletedFalse(signer.getActId()).orElse(null);
            if (act == null) continue;
            String sheetNumber = sheetRepository.findById(act.getSheetId()).map(InventoryLoanSheet::getSheetNumber).orElse("");
            expired.add(new ExpiredLink(act.getId(), sheetNumber, operation(act), signer.getDisplayName()));
            metrics.link("expired");
            LOG.info("signature_link_expired linkId={} actId={}", link.getId(), act.getId());
        }
        return expired;
    }

    /**
     * What the e-mail of a link of the act names.
     */
    SignatureLinkService.LinkContent linkContent(SignatureAct act, InventoryLoanSheet sheet) {
        return new SignatureLinkService.LinkContent(sheet.getSheetNumber(), operation(act), memberName(sheet));
    }

    private SignatureActSigner linkSigner(SignatureAct act, long signerId) {
        return signerRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId()).stream()
            .filter(signer -> signer.getId() == signerId && signer.getUserKeycloakId() == null && signer.getEmail() != null)
            .findFirst()
            .orElseThrow(LoanSheetSignatureService::actNotFound);
    }

    private ActFile documentFile(SignatureAct act, SignatureDocumentRole role, String tenant) {
        requireNotPurged(act);
        SignatureDocument document = documentRepository.findFirstByActIdAndRoleAndDeletedFalse(act.getId(), role).orElseThrow(LoanSheetSignatureService::actNotFound);
        Long media = document.getSealedMediaAssetId() != null ? document.getSealedMediaAssetId() : document.getFrozenMediaAssetId();
        if (media == null) throw purged();
        InventoryLoanSheet sheet = sheetRepository.findById(act.getSheetId()).orElseThrow(LoanSheetSignatureService::actNotFound);
        String suffix = (role == SignatureDocumentRole.CLAUSES ? "clausole" : "foglio") + (document.getSealedMediaAssetId() != null ? "-firmato" : "");
        return new ActFile(LoanSheetActDocuments.fileName(sheet, act, suffix), documents.read(media, tenant));
    }

    private ActFile attestationFile(SignatureAct act, String tenant) {
        requireNotPurged(act);
        if (act.getAttestationMediaAssetId() == null) throw actNotFound();
        InventoryLoanSheet sheet = sheetRepository.findById(act.getSheetId()).orElseThrow(LoanSheetSignatureService::actNotFound);
        return new ActFile(LoanSheetActDocuments.fileName(sheet, act, "attestazione"), documents.read(act.getAttestationMediaAssetId(), tenant));
    }

    /**
     * The dossier of a sheet: a ZIP with a folder for every signed act, holding the sealed documents and the
     * attestation, and an index with the SHA-256 hash of every file. Acts declared signed on paper are listed in the
     * index with the note of the declaration.
     */
    @Transactional(readOnly = true)
    public ActFile dossier(long sheetId, AbstractAuthenticationToken token) {
        String tenant = SignatureSupport.tenant(token);
        InventoryLoanSheet sheet = sheetRepository.findByIdAndDeletedFalse(sheetId).orElseThrow(
            () -> SignatureSupport.error(HttpStatus.NOT_FOUND, "Loan sheet not found", "inventory.loanSheet.notFound")
        );
        List<SignatureAct> completed = actRepository.findAllBySheetIdAndDeletedFalseOrderByIdAsc(sheetId).stream()
            .filter(act -> act.getStatus() == SignatureActStatus.SIGNED || act.getStatus() == SignatureActStatus.SIGNED_ON_PAPER)
            .toList();
        if (completed.isEmpty()) throw actNotFound();
        if (completed.stream().allMatch(act -> act.getPurgedAt() != null)) throw purged();
        StringBuilder index = new StringBuilder();
        index.append("Fascicolo del foglio di affidamento n. ").append(sheet.getSheetNumber()).append("\r\n");
        index.append("Generato il ").append(ZonedDateTime.now().toOffsetDateTime()).append("\r\n");
        index.append("La conservazione degli atti firmati è a cura dell'Associazione.\r\n\r\n");
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            int position = 0;
            for (SignatureAct act : completed) {
                position++;
                String folder = String.format(Locale.ROOT, "%02d-%s", position, folderName(act));
                index.append(folder).append(" – ").append(operation(act)).append("\r\n");
                if (act.getPurgedAt() != null) {
                    index.append("  Atto già eliminato da Taurus.\r\n\r\n");
                    continue;
                }
                if (act.getStatus() == SignatureActStatus.SIGNED_ON_PAPER) {
                    index.append("  Dichiarato firmato su carta: la copia cartacea è custodita dall'Associazione.\r\n");
                    index.append("  Nota: ").append(Objects.requireNonNullElse(act.getPaperNote(), "")).append("\r\n\r\n");
                    continue;
                }
                for (SignatureDocument document : documentRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId())) {
                    if (document.getSealedMediaAssetId() == null) continue;
                    String name = folder + "/" + LoanSheetActDocuments.fileName(sheet, act, document.getRole() == SignatureDocumentRole.CLAUSES ? "clausole-firmato" : "firmato");
                    byte[] bytes = documents.read(document.getSealedMediaAssetId(), tenant);
                    add(zip, name, bytes);
                    index.append("  ").append(name).append("  SHA-256 ").append(SignatureSupport.sha256(bytes)).append("\r\n");
                }
                if (act.getAttestationMediaAssetId() != null) {
                    String name = folder + "/" + LoanSheetActDocuments.fileName(sheet, act, "attestazione");
                    byte[] bytes = documents.read(act.getAttestationMediaAssetId(), tenant);
                    add(zip, name, bytes);
                    index.append("  ").append(name).append("  SHA-256 ").append(SignatureSupport.sha256(bytes)).append("\r\n");
                }
                index.append("\r\n");
            }
            add(zip, "INDICE.txt", index.toString().getBytes(StandardCharsets.UTF_8));
            zip.finish();
            LOG.info("signature_dossier_downloaded sheetId={} acts={}", sheetId, completed.size());
            return new ActFile("fascicolo-foglio-" + sheet.getSheetNumber().replace('/', '-') + ".zip", output.toByteArray());
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write the dossier", exception);
        }
    }

    // Support for the signatures

    SignatureAct sheetAct(long actId, boolean lock) {
        Optional<SignatureAct> act = lock ? actRepository.findForUpdate(actId) : actRepository.findByIdAndDeletedFalse(actId);
        return act.filter(value -> SHEET_ACTS.contains(value.getActType())).orElseThrow(LoanSheetSignatureService::actNotFound);
    }

    Optional<SignatureActSigner> signer(SignatureAct act, SignerRole role) {
        return signerRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId()).stream().filter(signer -> signer.getSignerRole() == role).findFirst();
    }

    boolean activeAdministrator(String userId) {
        return userId != null && usersRepository.findActiveKeycloakIdsByRolesIn(ADMIN_ROLES).contains(userId);
    }

    /**
     * Confirmations expected from a signer of an act in the given status, without those already recorded.
     */
    /** Only the holder enters data, for the issue, instead of filling in the paper form. */
    static boolean dataRequired(SignatureAct act, SignatureActSigner signer) {
        return signer.getSignerRole() == SignerRole.HOLDER && act.getActType() == SignatureActType.LOAN_SHEET_ISSUE && act.getStatus() == SignatureActStatus.AWAITING_DATA;
    }

    /**
     * Acts of loan sheets waiting for the user, as the box of the signatures lists them: data to enter or confirmations
     * to give as holder, minor member or signer for the association.
     */
    @Transactional(readOnly = true)
    public long countAwaitingUser(String userId) {
        if (userId == null) return 0;
        return actRepository.findAllForSigner(userId, SHEET_ACTS).stream()
            .filter(act -> act.getPurgedAt() == null && AWAITING.contains(act.getStatus()))
            .filter(act -> awaitsUser(act, userId))
            .count();
    }

    private boolean awaitsUser(SignatureAct act, String userId) {
        List<SignatureEvidence> evidence = evidenceRepository.findAllByActIdOrderBySequenceAsc(act.getId());
        return signerRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId()).stream()
            .filter(signer -> userId.equals(signer.getUserKeycloakId()))
            .anyMatch(signer -> {
                Set<SignaturePurpose> signed = evidence.stream()
                    .filter(value -> signer.getId().equals(value.getSignerId()))
                    .map(SignatureEvidence::getPurpose)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(SignaturePurpose.class)));
                return dataRequired(act, signer) || !pendingPurposes(act, signer, signed).isEmpty();
            });
    }

    static List<SignaturePurpose> pendingPurposes(SignatureAct act, SignatureActSigner signer, Set<SignaturePurpose> signed) {
        if (act.getStatus() != SignatureActStatus.AWAITING_SIGNATURES) return List.of();
        List<SignaturePurpose> expected = switch (signer.getSignerRole()) {
            case HOLDER -> act.getActType() == SignatureActType.LOAN_SHEET_ISSUE
                ? List.of(SignaturePurpose.ACCEPTANCE, SignaturePurpose.CLAUSE_APPROVAL)
                : List.of(SignaturePurpose.ACCEPTANCE);
            case ASSOCIATION -> List.of(SignaturePurpose.ASSOCIATION_SIGNATURE);
            case MINOR -> act.getActType() == SignatureActType.LOAN_SHEET_ISSUE ? List.of(SignaturePurpose.MINOR_ACKNOWLEDGMENT) : List.<SignaturePurpose>of();
            default -> List.of();
        };
        return expected.stream().filter(purpose -> !signed.contains(purpose)).toList();
    }

    /**
     * Description of the operation of an act, for example «Consegna successiva: Leggio pieghevole».
     */
    String operation(SignatureAct act) {
        return switch (act.getActType()) {
            case LOAN_SHEET_ISSUE -> "Emissione del foglio";
            case LOAN_SHEET_RETURN -> "Verbale di riconsegna";
            case ASSIGNMENT_ACKNOWLEDGMENT -> "Presa visione";
            case LOAN_SHEET_VARIATION -> variationLabel(act);
        };
    }

    static RequestAlertException actNotFound() {
        return SignatureSupport.error(HttpStatus.NOT_FOUND, "Act not found", "signature.act.notFound");
    }

    static RequestAlertException purged() {
        return SignatureSupport.error(HttpStatus.GONE, "The files of the act were removed", "signature.act.purged");
    }

    Act view(SignatureAct act, String currentUserId) {
        return view(act, signer -> currentUserId != null && currentUserId.equals(signer.getUserKeycloakId()), true);
    }

    /**
     * The view of an act: {@code current} tells the signer whose pending confirmations are listed, {@code links} adds
     * the links of the signers without an account, shown to the administrators.
     */
    private Act view(SignatureAct act, Predicate<SignatureActSigner> current, boolean links) {
        InventoryLoanSheet sheet = act.getSheetId() == null ? null : sheetRepository.findById(act.getSheetId()).orElse(null);
        List<SignatureActSigner> signers = signerRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId());
        Map<Long, LinkView> linkViews = links ? linkService.views(signers) : Map.of();
        List<SignatureEvidence> evidence = evidenceRepository.findAllByActIdOrderBySequenceAsc(act.getId());
        boolean purged = act.getPurgedAt() != null;
        List<ActDocument> views = documentRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId()).stream()
            .map(document -> new ActDocument(
                document.getRole(),
                document.getFrozenHash(),
                document.getSealedHash(),
                !purged && (document.getSealedMediaAssetId() != null || document.getFrozenMediaAssetId() != null)
            ))
            .toList();
        List<ActSigner> signerViews = new ArrayList<>();
        List<SignaturePurpose> pending = new ArrayList<>();
        boolean dataRequired = false;
        boolean holder = false;
        for (SignatureActSigner signer : signers) {
            Set<SignaturePurpose> signed = evidence.stream()
                .filter(value -> signer.getId().equals(value.getSignerId()))
                .map(SignatureEvidence::getPurpose)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(SignaturePurpose.class)));
            signerViews.add(new ActSigner(
                signer.getId(),
                signer.getSignerRole(),
                signer.getUserKeycloakId(),
                signer.getDisplayName(),
                signer.isRequired(),
                signer.getStatus(),
                signer.getSignedAt(),
                List.copyOf(signed),
                signer.getUserKeycloakId() == null ? SignatureSupport.maskEmail(signer.getEmail()) : null,
                linkViews.get(signer.getId())
            ));
            if (current.test(signer)) {
                pending.addAll(pendingPurposes(act, signer, signed));
                holder |= signer.getSignerRole() == SignerRole.HOLDER;
                dataRequired |= dataRequired(act, signer);
            }
        }
        return new Act(
            act.getId(),
            act.getActType(),
            act.getStatus(),
            act.getSheetId(),
            sheet == null ? null : sheet.getSheetNumber(),
            sheet == null ? null : sheet.getHolderRole(),
            operation(act),
            act.getInsertDate(),
            act.getCompletedAt(),
            act.getPurgeAfter(),
            act.getPurgedAt(),
            act.getPaperNote(),
            views,
            signerViews,
            !purged && act.getAttestationMediaAssetId() != null,
            AWAITING.contains(act.getStatus()) && evidence.isEmpty(),
            dataRequired,
            dataRequired && sheet != null ? memberBirthDate(sheet) : null,
            holder && (dataRequired || !pending.isEmpty()) ? pendingAcknowledgments(act.getSheetId()) : 0,
            pending,
            holder && sheet != null && sheet.getHolderTaxCode() != null && cieSignVerifier.available(),
            act.getEntityVersion()
        );
    }

    LoanSheetActChange change(InventoryLoanSheet sheet, SignatureAct act, List<String> recipients, ZonedDateTime purgeAfter) {
        return new LoanSheetActChange(
            sheet.getId(),
            sheet.getSheetNumber(),
            act == null ? null : act.getId(),
            act == null ? null : act.getActType(),
            act == null ? null : operation(act),
            recipients.stream().filter(Objects::nonNull).distinct().toList(),
            purgeAfter
        );
    }

    private SignatureAct ownedAct(long actId, String userId) {
        SignatureAct act = sheetAct(actId, false);
        boolean signer = signerRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId()).stream().anyMatch(value -> userId.equals(value.getUserKeycloakId()));
        if (!signer) throw actNotFound();
        return act;
    }

    private static void requireNotPurged(SignatureAct act) {
        if (act.getPurgedAt() != null) throw purged();
    }

    private AssociationSigner associationSigner(String userId, String holderUserId) {
        RequestAlertException notAdmin = SignatureSupport.error(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "The signer for the association is not an active administrator",
            "signature.associationSigner.notAdmin"
        );
        if (userId == null || userId.isBlank()) throw notAdmin;
        if (userId.equals(holderUserId)) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "The holder cannot sign for the association", "signature.associationSigner.sameAsHolder");
        }
        if (!activeAdministrator(userId)) throw notAdmin;
        Users user = usersRepository.findByKeycloakIdAndDeletedFalse(userId).orElseThrow(() -> notAdmin);
        return new AssociationSigner(userId, displayName(user));
    }

    /**
     * Who signs a variation or the return for the association: the administrator who records it or, when that is the
     * holder or no longer an administrator, the signer designated at issue.
     */
    private AssociationSigner operationSigner(InventoryLoanSheet sheet, String actor) {
        if (!actor.equals(sheet.getUserKeycloakId()) && activeAdministrator(actor)) {
            Optional<AssociationSigner> recorder = usersRepository.findByKeycloakIdAndDeletedFalse(actor).map(user -> new AssociationSigner(actor, displayName(user)));
            if (recorder.isPresent()) return recorder.orElseThrow();
        }
        return actRepository.findFirstBySheetIdAndActTypeAndDeletedFalseOrderByIdDesc(sheet.getId(), SignatureActType.LOAN_SHEET_ISSUE)
            .flatMap(issue -> signer(issue, SignerRole.ASSOCIATION))
            .map(signer -> new AssociationSigner(signer.getUserKeycloakId(), signer.getDisplayName()))
            .orElseThrow(() -> SignatureSupport.error(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "No administrator can sign for the association",
                "signature.associationSigner.notAdmin"
            ));
    }

    private SignatureAct newAct(SignatureActType type, SignatureActStatus status, InventoryLoanSheet sheet, String actor) {
        SignatureAct act = new SignatureAct();
        act.initializeAudit(actor);
        act.setActType(type);
        act.setStatus(status);
        act.setSheetId(sheet.getId());
        act.setTemplateId(sheet.getTemplate().getId());
        act.setTemplateHash(sheet.getTemplateHash());
        return actRepository.saveAndFlush(act);
    }

    /**
     * Adds the holder of the sheet: the adult member with the own account, or the parent or guardian, without an
     * account, reached at the address entered at issue.
     */
    private SignatureActSigner addHolder(SignatureAct act, InventoryLoanSheet sheet, String actor) {
        return guardian(sheet)
            ? addSigner(act, SignerRole.HOLDER, null, holderName(sheet), sheet.getHolderEmail(), true, actor)
            : addSigner(act, SignerRole.HOLDER, sheet.getUserKeycloakId(), holderName(sheet), null, true, actor);
    }

    private SignatureActSigner addSigner(SignatureAct act, SignerRole role, String userId, String displayName, String email, boolean required, String actor) {
        SignatureActSigner signer = new SignatureActSigner();
        signer.initializeAudit(actor);
        signer.setActId(act.getId());
        signer.setSignerRole(role);
        signer.setRequired(required);
        signer.setUserKeycloakId(userId);
        signer.setDisplayName(displayName);
        signer.setEmail(email);
        signer.setStatus(SignerStatus.PENDING);
        return signerRepository.saveAndFlush(signer);
    }

    /**
     * Sends the link of the act to a holder without an account, after the commit.
     */
    private void sendLink(SignatureAct act, InventoryLoanSheet sheet, SignatureActSigner holder, String actor) {
        if (holder.getUserKeycloakId() != null || holder.getEmail() == null) return;
        linkService.createAndSendAfterCommit(holder, linkContent(act, sheet), false, actor);
    }

    private void created(SignatureAct act, InventoryLoanSheet sheet) {
        metrics.act(act.getActType(), "created");
        LOG.info("signature_act_created actId={} type={} sheetId={}", act.getId(), act.getActType(), sheet.getId());
    }

    private void cancel(SignatureAct act, String tenantCode, String actor) {
        for (SignatureDocument document : documentRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId())) {
            documents.removeFiles(document, tenantCode, actor);
        }
        // The links stop working and the address of a parent is no longer needed.
        List<SignatureActSigner> signers = signerRepository.findAllByActIdAndDeletedFalseOrderByIdAsc(act.getId());
        linkService.purge(signers, actor);
        for (SignatureActSigner signer : signers) {
            if (signer.getEmail() == null) continue;
            signer.setEmail(null);
            signer.touchAudit(actor);
            signerRepository.save(signer);
        }
        act.setStatus(SignatureActStatus.CANCELLED);
        act.setCancelledAt(ZonedDateTime.now());
        act.touchAudit(actor);
        actRepository.save(act);
        metrics.act(act.getActType(), "cancelled");
        LOG.info("signature_act_cancelled actId={}", act.getId());
    }

    private void clearHolderData(InventoryLoanSheet sheet, String actor) {
        if (sheet.getHolderEmail() == null && sheet.getHolderName() == null && sheet.getHolderTaxCode() == null && !sheet.isHolderNoTaxCode()) return;
        sheet.setHolderEmail(null);
        sheet.setHolderName(null);
        sheet.setHolderTaxCode(null);
        sheet.setHolderNoTaxCode(false);
        sheet.touchAudit(actor);
        sheetRepository.save(sheet);
    }

    private boolean nothingOutstanding(InventoryLoanSheet sheet) {
        List<InventoryAssignment> assignments = assignmentRepository.findAllByLoanSheetIdAndDeletedFalseOrderByAssignedAtAscIdAsc(sheet.getId());
        return !assignments.isEmpty() && assignments.stream().allMatch(assignment -> assignment.getOutstandingQuantity() == 0);
    }

    private String variationLabel(SignatureAct act) {
        if (act.getAssignmentId() != null) {
            return assignmentRepository.findById(act.getAssignmentId())
                .map(assignment -> (assignment.getReplacesAssignmentId() != null ? "Sostituzione: " : "Consegna successiva: ") + assignment.getItem().getName())
                .orElse("Variazione");
        }
        if (act.getRenewalId() != null) {
            return renewalRepository.findById(act.getRenewalId())
                .map(renewal -> "Rinnovo: " + renewal.getAssignment().getItem().getName() + " fino al " + DATE.format(renewal.getNewExpirationDate()))
                .orElse("Rinnovo");
        }
        if (act.getReturnId() != null) {
            return returnRepository.findById(act.getReturnId())
                .map(value -> "Restituzione parziale: " + value.getAssignment().getItem().getName() + (value.getQuantity() > 1 ? " (" + value.getQuantity() + ")" : ""))
                .orElse("Restituzione parziale");
        }
        return "Variazione";
    }

    private static String folderName(SignatureAct act) {
        return switch (act.getActType()) {
            case LOAN_SHEET_ISSUE -> "emissione";
            case LOAN_SHEET_VARIATION -> "variazione-" + act.getId();
            case LOAN_SHEET_RETURN -> "riconsegna";
            case ASSIGNMENT_ACKNOWLEDGMENT -> "presa-visione-" + act.getId();
        };
    }

    private static void add(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    /**
     * Name of the holder: the adult member or the parent or guardian, named from the data entered for the issue.
     */
    private static String holderName(InventoryLoanSheet sheet) {
        if (!guardian(sheet)) return memberName(sheet);
        return sheet.getHolderName() != null ? sheet.getHolderName() : "Genitore o tutore di " + memberName(sheet);
    }

    private static String memberName(InventoryLoanSheet sheet) {
        String name = LoanSheetText.join(" ", sheet.getMemberName(), sheet.getMemberLastName());
        return name == null || name.isBlank() ? sheet.getUserKeycloakId() : name;
    }

    private static boolean guardian(InventoryLoanSheet sheet) {
        return sheet.getHolderRole() == InventoryLoanHolderRole.GUARDIAN;
    }

    /**
     * Acknowledgments the member still owes on the outstanding assignments of the sheet: the holder signs the acts of
     * the sheet only once the member has acknowledged every item.
     */
    long pendingAcknowledgments(Long sheetId) {
        return sheetId == null ? 0 : assignmentRepository.countPendingDecisionsOfSheet(sheetId, OUTSTANDING_ASSIGNMENTS);
    }

    /**
     * Refuses the data and the signatures of the holder while the member has acknowledgments to confirm on the sheet.
     */
    void requireNoPendingAcknowledgments(Long sheetId) {
        long acknowledgments = pendingAcknowledgments(sheetId);
        if (acknowledgments > 0) {
            throw SignatureSupport.error(HttpStatus.CONFLICT, "The member has acknowledgments to confirm before the signature", "signature.act.acknowledgmentsPending")
                .with("count", acknowledgments);
        }
    }

    /**
     * Date of birth of the member in the user profile, where the member enters it: the documents of the issue report it
     * for the adult holder or for the minor.
     */
    private LocalDate memberBirthDate(InventoryLoanSheet sheet) {
        return usersRepository.findByKeycloakIdAndDeletedFalse(sheet.getUserKeycloakId())
            .map(Users::getBirthDate)
            .map(LoanSheetSignatureService::localDate)
            .orElse(null);
    }

    /** The column is a date: Hibernate loads it as {@link java.sql.Date}, at midnight in the default time zone. */
    static LocalDate localDate(java.util.Date value) {
        return value instanceof java.sql.Date date ? date.toLocalDate() : LocalDate.ofInstant(value.toInstant(), java.time.ZoneId.systemDefault());
    }

    static String displayName(Users user) {
        String name = (Objects.requireNonNullElse(user.getName(), "") + " " + Objects.requireNonNullElse(user.getLastName(), "")).trim();
        return name.isEmpty() ? user.getKeycloakId() : name;
    }

    /**
     * Checks the data of the holder; the names of the missing or invalid fields go in {@code args.fields}. A parent or
     * guardian enters also the own name and date of birth, as an adult, and the birthplace of the minor.
     */
    static HolderData holderData(HolderDataRequest request, InventoryLoanHolderRole role, String holderEmail) {
        List<String> invalid = new ArrayList<>();
        boolean guardian = role == InventoryLoanHolderRole.GUARDIAN;
        String firstName = guardian ? required(request.holderFirstName(), "holderFirstName", invalid) : null;
        String lastName = guardian ? required(request.holderLastName(), "holderLastName", invalid) : null;
        LocalDate birthDate = guardian ? request.holderBirthDate() : null;
        if (guardian && (birthDate == null || birthDate.isAfter(LocalDate.now().minusYears(18)))) invalid.add("holderBirthDate");
        String birthPlace = required(request.birthPlace(), "birthPlace", invalid);
        String residence = required(request.residence(), "residence", invalid);
        String postalCode = required(request.postalCode(), "postalCode", invalid);
        if (postalCode != null && !POSTAL_CODE.matcher(postalCode).matches()) invalid.add("postalCode");
        String cityProvince = required(request.cityProvince(), "cityProvince", invalid);
        String taxCode = null;
        if (!request.noTaxCode()) {
            taxCode = ItalianTaxCode.normalize(SignatureSupport.trimToNull(request.taxCode()));
            if (!ItalianTaxCode.valid(taxCode)) invalid.add("taxCode");
        }
        String document = required(request.document(), "document", invalid);
        String issuedBy = required(request.documentIssuedBy(), "documentIssuedBy", invalid);
        LocalDate issuedOn = request.documentIssuedOn();
        if (issuedOn == null || issuedOn.isAfter(LocalDate.now())) invalid.add("documentIssuedOn");
        String minorBirthPlace = guardian ? required(request.minorBirthPlace(), "minorBirthPlace", invalid) : null;
        if (!invalid.isEmpty()) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid holder data", "signature.holderData.invalid").with("fields", List.copyOf(new LinkedHashSet<>(invalid)));
        }
        return new HolderData(
            birthPlace,
            residence,
            postalCode,
            cityProvince,
            taxCode,
            document,
            issuedBy,
            issuedOn,
            SignatureSupport.trimToNull(request.phone()),
            minorBirthPlace,
            guardian ? LoanSheetText.join(" ", firstName, lastName) : null,
            birthDate,
            guardian ? holderEmail : null
        );
    }

    private static String required(String value, String field, List<String> invalid) {
        String trimmed = SignatureSupport.trimToNull(value);
        if (trimmed == null) invalid.add(field);
        return trimmed;
    }
}
