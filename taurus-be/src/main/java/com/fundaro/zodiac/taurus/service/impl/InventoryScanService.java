package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueSeverity;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryIssueReportRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemPhotoRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanAction;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanAssignment;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanResponse;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanTarget;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryScanService {
    private static final List<InventoryIssueStatus> OPEN_ISSUE_STATUSES = List.of(InventoryIssueStatus.OPEN, InventoryIssueStatus.ACKNOWLEDGED);
    private final InventoryItemRepository itemRepository;
    private final InventoryAssignmentRepository assignmentRepository;
    private final InventoryItemPhotoRepository photoRepository;
    private final InventoryIssueReportRepository issueRepository;
    private final InventoryQrCodeService qrCodeService;

    public InventoryScanService(
        InventoryItemRepository itemRepository,
        InventoryAssignmentRepository assignmentRepository,
        InventoryItemPhotoRepository photoRepository,
        InventoryIssueReportRepository issueRepository,
        InventoryQrCodeService qrCodeService
    ) {
        this.itemRepository = itemRepository;
        this.assignmentRepository = assignmentRepository;
        this.photoRepository = photoRepository;
        this.issueRepository = issueRepository;
        this.qrCodeService = qrCodeService;
    }

    public ScanResponse resolve(String rawPublicId, AbstractAuthenticationToken token) {
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
        if (isAdministrator(token)) {
            int assigned = Math.toIntExact(assignmentRepository.sumOutstanding(item.getId(), InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES));
            long openIssues = issueRepository.countByItem_IdAndDeletedFalseAndStatusIn(item.getId(), OPEN_ISSUE_STATUSES);
            boolean unsafe = issueRepository.existsByItem_IdAndSeverityAndStatusInAndDeletedFalse(item.getId(), InventoryIssueSeverity.UNSAFE, OPEN_ISSUE_STATUSES);
            List<ScanAction> actions = new ArrayList<>(List.of(ScanAction.ADD_ITEM_PHOTO, ScanAction.REPORT_ISSUE, ScanAction.PRINT_LABEL, ScanAction.ROTATE_CODE));
            if (assigned > 0) actions.add(ScanAction.RETURN);
            if (!unsafe && assigned < item.getTotalQuantity()) actions.add(ScanAction.ASSIGN);
            return new ScanResponse(ScanTarget.ADMIN_ITEM, item.getId(), item.getInventoryNumber(), item.getName(), item.getConditionStatus(),
                item.getTotalQuantity(), assigned, item.getTotalQuantity() - assigned, previewPhotoId, openIssues, unsafe,
                List.copyOf(actions), List.of());
        }
        String userId = actor(token);
        List<ScanAssignment> assignments = assignmentRepository.findAllByUserKeycloakIdAndDeletedFalseOrderByAssignedAtDesc(userId).stream()
            .filter(assignment -> assignment.getItem().getId().equals(item.getId()))
            .map(assignment -> toScanAssignment(assignment, previewPhotoId)).toList();
        if (assignments.isEmpty()) throw notFound();
        return new ScanResponse(ScanTarget.OWN_ASSIGNMENTS, null, null, null, null, null, null, null, null, null, null, List.of(), assignments);
    }

    private ScanAssignment toScanAssignment(InventoryAssignment assignment, Long previewPhotoId) {
        List<ScanAction> actions = new java.util.ArrayList<>();
        actions.add(ScanAction.VIEW);
        if (assignment.getOutstandingQuantity() > 0) {
            actions.add(ScanAction.REQUEST_RETURN);
            actions.add(ScanAction.REPORT_ISSUE);
        }
        return new ScanAssignment(assignment.getId(), assignment.getItem().getInventoryNumber(), assignment.getItem().getName(),
            assignment.getOutstandingQuantity(), assignment.getStatus(), previewPhotoId, List.copyOf(actions));
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
    private static RequestAlertException notFound() {
        return new RequestAlertException(HttpStatus.NOT_FOUND, "Codice inventario non disponibile", "inventoryScan", "inventory.qr.notFound");
    }
}
