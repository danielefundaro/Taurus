package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueSeverity;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnStatus;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryIssueReportRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemPhotoRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnRepository;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanAction;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanAssignment;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanResponse;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanTarget;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryScanService {
    private static final Logger LOG = LoggerFactory.getLogger(InventoryScanService.class);
    private static final List<InventoryIssueStatus> OPEN_ISSUE_STATUSES = List.of(InventoryIssueStatus.OPEN, InventoryIssueStatus.ACKNOWLEDGED);
    /** La stessa soglia del badge delle scadenze del frontend: oltre, una scadenza non è ancora un avviso. */
    static final int EXPIRING_WITHIN_DAYS = 30;
    private final InventoryItemRepository itemRepository;
    private final InventoryAssignmentRepository assignmentRepository;
    private final InventoryItemPhotoRepository photoRepository;
    private final InventoryIssueReportRepository issueRepository;
    private final InventoryReturnRepository returnRepository;
    private final InventoryQrCodeService qrCodeService;
    private final InventoryQrMetrics metrics;

    public InventoryScanService(
        InventoryItemRepository itemRepository,
        InventoryAssignmentRepository assignmentRepository,
        InventoryItemPhotoRepository photoRepository,
        InventoryIssueReportRepository issueRepository,
        InventoryReturnRepository returnRepository,
        InventoryQrCodeService qrCodeService,
        InventoryQrMetrics metrics
    ) {
        this.itemRepository = itemRepository;
        this.assignmentRepository = assignmentRepository;
        this.photoRepository = photoRepository;
        this.issueRepository = issueRepository;
        this.returnRepository = returnRepository;
        this.qrCodeService = qrCodeService;
        this.metrics = metrics;
    }

    /**
     * Risolve un codice nel tenant del token. Ogni esito negativo — codice malformato, sconosciuto, ruotato, oggetto
     * cancellato, nessuna assegnazione dell'utente — è lo stesso {@code 404}, e il log ne registra solo un'impronta
     * breve: l'UUID completo non compare mai.
     */
    public ScanResponse resolve(String rawPublicId, AbstractAuthenticationToken token) {
        long started = System.nanoTime();
        String roleGroup = roleGroup(token);
        try {
            ScanResponse response = lookup(rawPublicId, token);
            metrics.resolution("resolved", roleGroup, elapsedMillis(started));
            return response;
        } catch (RequestAlertException exception) {
            String outcome = "inventory.qr.disabled".equals(exception.getErrorKey()) ? "disabled" : "not_found";
            metrics.resolution(outcome, roleGroup, elapsedMillis(started));
            LOG.info("inventory_qr_resolution_failed outcome={} roleGroup={} codeHash={}", outcome, roleGroup, shortHash(rawPublicId));
            throw exception;
        }
    }

    private ScanResponse lookup(String rawPublicId, AbstractAuthenticationToken token) {
        qrCodeService.requireEnabled();
        tenant(token);
        UUID publicId;
        try {
            publicId = UUID.fromString(rawPublicId);
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
        InventoryItem item = itemRepository.findByQrPublicIdAndDeletedFalse(publicId).orElseThrow(InventoryScanService::notFound);
        Long previewPhotoId = photoRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(item.getId()).stream()
            .filter(photo -> photo.isPreview()).findFirst().map(photo -> photo.getId()).orElse(null);
        return isAdministrator(token) ? administratorView(item, previewPhotoId) : memberView(item, previewPhotoId, actor(token));
    }

    private ScanResponse administratorView(InventoryItem item, Long previewPhotoId) {
        int assigned = Math.toIntExact(assignmentRepository.sumOutstanding(item.getId(), InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES));
        long openIssues = issueRepository.countByItem_IdAndDeletedFalseAndStatusIn(item.getId(), OPEN_ISSUE_STATUSES);
        boolean unsafe = issueRepository.existsByItem_IdAndSeverityAndStatusInAndDeletedFalse(item.getId(), InventoryIssueSeverity.UNSAFE, OPEN_ISSUE_STATUSES);
        LocalDate today = LocalDate.now();
        LocalDate expiringLimit = today.plusDays(EXPIRING_WITHIN_DAYS);
        List<LocalDate> expirations = assignmentRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(item.getId()).stream()
            .filter(assignment -> InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES.contains(assignment.getStatus()))
            .filter(assignment -> assignment.getOutstandingQuantity() > 0 && assignment.getExpirationDate() != null)
            .map(InventoryAssignment::getExpirationDate).toList();
        int overdue = (int) expirations.stream().filter(date -> date.isBefore(today)).count();
        int expiring = (int) expirations.stream().filter(date -> !date.isBefore(today) && !date.isAfter(expiringLimit)).count();
        long pendingDecisions = assignmentRepository.countPendingDecisionsOfItem(item.getId(), InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES);
        List<ScanAction> actions = new ArrayList<>();
        if (!unsafe && assigned < item.getTotalQuantity()) actions.add(ScanAction.ASSIGN);
        if (assigned > 0) actions.add(ScanAction.RETURN);
        actions.addAll(List.of(ScanAction.ADD_ITEM_PHOTO, ScanAction.REPORT_ISSUE, ScanAction.PRINT_LABEL, ScanAction.ROTATE_CODE));
        return new ScanResponse(ScanTarget.ADMIN_ITEM, item.getId(), item.getInventoryNumber(), item.getName(), item.getConditionStatus(),
            item.getTotalQuantity(), assigned, item.getTotalQuantity() - assigned, previewPhotoId, openIssues, unsafe,
            overdue, expiring, EXPIRING_WITHIN_DAYS, pendingDecisions, List.copyOf(actions), List.of());
    }

    private ScanResponse memberView(InventoryItem item, Long previewPhotoId, String userId) {
        List<InventoryAssignment> assignments = assignmentRepository.findAllByUserKeycloakIdAndDeletedFalseOrderByAssignedAtDesc(userId).stream()
            .filter(assignment -> assignment.getItem().getId().equals(item.getId())).toList();
        if (assignments.isEmpty()) throw notFound();
        List<Long> outstanding = assignments.stream()
            .filter(assignment -> InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES.contains(assignment.getStatus()))
            .map(InventoryAssignment::getId).toList();
        Set<Long> pendingDecisions = outstanding.isEmpty() ? Set.of() : Set.copyOf(assignmentRepository.findIdsWithPendingDecision(outstanding));
        List<ScanAssignment> result = assignments.stream()
            .map(assignment -> toScanAssignment(assignment, previewPhotoId, pendingDecisions.contains(assignment.getId()))).toList();
        return new ScanResponse(ScanTarget.OWN_ASSIGNMENTS, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, List.of(), result);
    }

    /**
     * Le azioni ripetono i vincoli dei comandi che il frontend apre: la riconsegna solo per la quantità non già
     * richiesta, le fotografie solo dove esiste una riconsegna o una segnalazione aperta. Restano indicazioni di
     * presentazione: ogni comando rifà i propri controlli.
     */
    private ScanAssignment toScanAssignment(InventoryAssignment assignment, Long previewPhotoId, boolean decisionPending) {
        boolean open = InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES.contains(assignment.getStatus());
        long requested = open ? returnRepository.sumQuantities(assignment.getId(), List.of(InventoryReturnStatus.REQUESTED)) : 0;
        List<ScanAction> actions = new ArrayList<>();
        actions.add(ScanAction.VIEW);
        if (open && decisionPending) actions.add(ScanAction.COMPLETE_DECISION);
        if (assignment.getOutstandingQuantity() > requested) actions.add(ScanAction.REQUEST_RETURN);
        if (requested > 0) actions.add(ScanAction.ADD_RETURN_PHOTO);
        if (assignment.getOutstandingQuantity() > 0) actions.add(ScanAction.REPORT_ISSUE);
        if (issueRepository.existsByAssignment_IdAndStatusInAndDeletedFalse(assignment.getId(), OPEN_ISSUE_STATUSES))
            actions.add(ScanAction.ADD_ISSUE_PHOTO);
        return new ScanAssignment(assignment.getId(), assignment.getItem().getInventoryNumber(), assignment.getItem().getName(),
            assignment.getItem().getConditionStatus(), assignment.getOutstandingQuantity(), assignment.getStatus(),
            assignment.getExpirationDate(), open && decisionPending, previewPhotoId, List.copyOf(actions));
    }

    /** Il gruppo di ruolo delle metriche: {@code admin} per amministratori e super amministratori, altrimenti {@code member}. */
    public static String roleGroup(AbstractAuthenticationToken token) {
        return isAdministrator(token) ? "admin" : "member";
    }

    private static boolean isAdministrator(AbstractAuthenticationToken token) {
        return token.getAuthorities().stream().anyMatch(authority -> AuthoritiesConstants.ADMIN.equals(authority.getAuthority()) || AuthoritiesConstants.SUPER_ADMIN.equals(authority.getAuthority()));
    }

    private static String actor(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getUserIdFromAuthentication(token);
        if (value == null || value.isBlank()) throw notFound();
        return value;
    }

    private static void tenant(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getTenantIdFromAuthentication(token);
        if (value == null || value.isBlank()) throw notFound();
    }

    /**
     * I primi dodici caratteri esadecimali dello SHA-256: bastano a correlare le righe di log, non a risalire al codice
     * o all'utente. Lo usano anche etichette, rotazioni e guasti per l'autore.
     */
    static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static RequestAlertException notFound() {
        return new RequestAlertException(HttpStatus.NOT_FOUND, "Codice inventario non disponibile", "inventoryScan", "inventory.qr.notFound");
    }
}
