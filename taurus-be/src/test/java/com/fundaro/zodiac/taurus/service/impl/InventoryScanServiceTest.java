package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueSeverity;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItemPhoto;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryIssueReportRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemPhotoRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnRepository;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanAction;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanAssignment;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanResponse;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryQrDtos.ScanTarget;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InventoryScanServiceTest {
    private static final UUID CODE = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private final InventoryItemRepository items = mock(InventoryItemRepository.class);
    private final InventoryAssignmentRepository assignments = mock(InventoryAssignmentRepository.class);
    private final InventoryItemPhotoRepository photos = mock(InventoryItemPhotoRepository.class);
    private final InventoryIssueReportRepository issues = mock(InventoryIssueReportRepository.class);
    private final InventoryReturnRepository returns = mock(InventoryReturnRepository.class);
    private final InventoryQrCodeService qrCodes = mock(InventoryQrCodeService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final InventoryScanService service = new InventoryScanService(items, assignments, photos, issues, returns, qrCodes,
        new InventoryQrMetrics(registry));
    private final InventoryItem item = item();

    @BeforeEach
    void setUp() {
        when(items.findByQrPublicIdAndDeletedFalse(CODE)).thenReturn(Optional.of(item));
        InventoryItemPhoto other = new InventoryItemPhoto();
        other.setId(10L);
        InventoryItemPhoto preview = new InventoryItemPhoto();
        preview.setId(11L);
        preview.setPreview(true);
        when(photos.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(42L)).thenReturn(List.of(other, preview));
    }

    @Test
    void administratorsSeeQuantitiesAlertsAndEveryAvailableAction() {
        LocalDate today = LocalDate.now();
        when(assignments.sumOutstanding(eq(42L), any())).thenReturn(3L);
        when(issues.countByItem_IdAndDeletedFalseAndStatusIn(eq(42L), any())).thenReturn(2L);
        when(assignments.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(42L)).thenReturn(List.of(
            assignment(1L, "a", InventoryAssignmentStatus.ACTIVE, 1, today.minusDays(1)),
            assignment(2L, "b", InventoryAssignmentStatus.ACTIVE, 1, today.plusDays(30)),
            assignment(3L, "c", InventoryAssignmentStatus.PARTIALLY_RETURNED, 1, today.plusDays(31)),
            assignment(4L, "d", InventoryAssignmentStatus.RETURNED, 0, today.minusDays(9))
        ));
        when(assignments.countPendingDecisionsOfItem(eq(42L), any())).thenReturn(1L);

        ScanResponse response = service.resolve(CODE.toString(), authentication("admin-1", "ROLE_ADMIN"));

        assertThat(response.target()).isEqualTo(ScanTarget.ADMIN_ITEM);
        assertThat(response.conditionStatus()).isEqualTo(InventoryCondition.FAIR);
        assertThat(response.availableQuantity()).isEqualTo(2);
        assertThat(response.previewPhotoId()).isEqualTo(11L);
        assertThat(response.openIssueCount()).isEqualTo(2);
        assertThat(response.overdueAssignments()).isEqualTo(1);
        assertThat(response.expiringAssignments()).isEqualTo(1);
        assertThat(response.expiringWithinDays()).isEqualTo(30);
        assertThat(response.pendingDecisions()).isEqualTo(1);
        assertThat(response.allowedActions()).containsExactly(ScanAction.ASSIGN, ScanAction.RETURN, ScanAction.ADD_ITEM_PHOTO,
            ScanAction.REPORT_ISSUE, ScanAction.PRINT_LABEL, ScanAction.ROTATE_CODE);
        assertThat(registry.get("taurus.inventory.qr.resolution").tags("outcome", "resolved", "role_group", "admin").counter().count()).isEqualTo(1);
    }

    @Test
    void anOpenUnsafeIssueOrAFullAssignmentRemovesAssign() {
        when(assignments.sumOutstanding(eq(42L), any())).thenReturn(0L);
        when(issues.existsByItem_IdAndSeverityAndStatusInAndDeletedFalse(eq(42L), eq(InventoryIssueSeverity.UNSAFE), any())).thenReturn(true);

        ScanResponse unsafe = service.resolve(CODE.toString(), authentication("admin-1", "ROLE_SUPER_ADMIN"));

        assertThat(unsafe.unsafe()).isTrue();
        assertThat(unsafe.allowedActions()).doesNotContain(ScanAction.ASSIGN, ScanAction.RETURN);

        when(issues.existsByItem_IdAndSeverityAndStatusInAndDeletedFalse(anyLong(), any(), any())).thenReturn(false);
        when(assignments.sumOutstanding(eq(42L), any())).thenReturn(5L);
        assertThat(service.resolve(CODE.toString(), authentication("admin-1", "ROLE_ADMIN")).allowedActions())
            .doesNotContain(ScanAction.ASSIGN).contains(ScanAction.RETURN);
    }

    @Test
    void membersSeeOnlyTheirOwnAssignmentsWithTheActionsTheirStateAllows() {
        InventoryAssignment pending = assignment(7L, "member-1", InventoryAssignmentStatus.ACTIVE, 2, LocalDate.now().plusDays(3));
        InventoryAssignment requested = assignment(8L, "member-1", InventoryAssignmentStatus.PARTIALLY_RETURNED, 1, null);
        InventoryAssignment closed = assignment(9L, "member-1", InventoryAssignmentStatus.RETURNED, 0, null);
        InventoryAssignment otherItem = assignment(10L, "member-1", InventoryAssignmentStatus.ACTIVE, 1, null);
        InventoryItem another = item();
        another.setId(43L);
        otherItem.setItem(another);
        when(assignments.findAllByUserKeycloakIdAndDeletedFalseOrderByAssignedAtDesc("member-1")).thenReturn(List.of(pending, requested, closed, otherItem));
        when(assignments.findIdsWithPendingDecision(List.of(7L, 8L))).thenReturn(List.of(7L));
        when(returns.sumQuantities(eq(8L), any())).thenReturn(1L);
        when(issues.existsByAssignment_IdAndStatusInAndDeletedFalse(eq(7L), any())).thenReturn(true);

        ScanResponse response = service.resolve(CODE.toString(), authentication("member-1", "ROLE_USER"));

        assertThat(response.target()).isEqualTo(ScanTarget.OWN_ASSIGNMENTS);
        assertThat(response.itemId()).isNull();
        assertThat(response.allowedActions()).isEmpty();
        assertThat(response.assignments()).extracting(ScanAssignment::assignmentId).containsExactly(7L, 8L, 9L);
        ScanAssignment first = response.assignments().get(0);
        assertThat(first.decisionPending()).isTrue();
        assertThat(first.previewPhotoId()).isEqualTo(11L);
        assertThat(first.allowedActions()).containsExactly(ScanAction.VIEW, ScanAction.COMPLETE_DECISION, ScanAction.REQUEST_RETURN,
            ScanAction.REPORT_ISSUE, ScanAction.ADD_ISSUE_PHOTO);
        assertThat(response.assignments().get(1).allowedActions()).containsExactly(ScanAction.VIEW, ScanAction.ADD_RETURN_PHOTO, ScanAction.REPORT_ISSUE);
        assertThat(response.assignments().get(2).allowedActions()).containsExactly(ScanAction.VIEW);
        verify(returns, never()).sumQuantities(eq(9L), any());
        assertThat(registry.get("taurus.inventory.qr.resolution").tags("outcome", "resolved", "role_group", "member").counter().count()).isEqualTo(1);
    }

    @Test
    void closedAssignmentsDoNotQueryPendingDecisions() {
        when(assignments.findAllByUserKeycloakIdAndDeletedFalseOrderByAssignedAtDesc("member-1"))
            .thenReturn(List.of(assignment(9L, "member-1", InventoryAssignmentStatus.RETURNED, 0, null)));

        assertThat(service.resolve(CODE.toString(), authentication("member-1", "ROLE_USER")).assignments()).hasSize(1);
        verify(assignments, never()).findIdsWithPendingDecision(any());
    }

    @Test
    void everyFailureIsTheSameNotFoundAndIsCounted() {
        when(assignments.findAllByUserKeycloakIdAndDeletedFalseOrderByAssignedAtDesc("member-2")).thenReturn(List.of());

        assertNotFound("not-a-uuid", authentication("admin-1", "ROLE_ADMIN"));
        assertNotFound(UUID.randomUUID().toString(), authentication("admin-1", "ROLE_ADMIN"));
        assertNotFound(CODE.toString(), authentication("member-2", "ROLE_USER"));
        assertThat(registry.get("taurus.inventory.qr.resolution").tags("outcome", "not_found", "role_group", "admin").counter().count()).isEqualTo(2);
        assertThat(registry.get("taurus.inventory.qr.resolution").tags("outcome", "not_found", "role_group", "member").counter().count()).isEqualTo(1);
    }

    @Test
    void aDisabledFeatureIsCountedApart() {
        doThrow(new RequestAlertException(HttpStatus.NOT_FOUND, "off", "inventoryQrCode", "inventory.qr.disabled")).when(qrCodes).requireEnabled();

        assertThatThrownBy(() -> service.resolve(CODE.toString(), authentication("admin-1", "ROLE_ADMIN"))).isInstanceOf(RequestAlertException.class);
        assertThat(registry.get("taurus.inventory.qr.resolution").tags("outcome", "disabled").counter().count()).isEqualTo(1);
    }

    @Test
    void theShortHashIsStableAndDoesNotRevealTheValue() {
        assertThat(InventoryScanService.shortHash(CODE.toString())).hasSize(12).isEqualTo(InventoryScanService.shortHash(CODE.toString()))
            .doesNotContain("550e8400");
        assertThat(InventoryScanService.shortHash(null)).hasSize(12);
    }

    private void assertNotFound(String code, JwtAuthenticationToken token) {
        assertThatThrownBy(() -> service.resolve(code, token))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> {
                assertThat(error.getErrorKey()).isEqualTo("inventory.qr.notFound");
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            });
    }

    private InventoryAssignment assignment(long id, String user, InventoryAssignmentStatus status, int outstanding, LocalDate expiration) {
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setId(id);
        assignment.setItem(item);
        assignment.setUserKeycloakId(user);
        assignment.setStatus(status);
        assignment.setAssignedQuantity(Math.max(outstanding, 1));
        assignment.setReturnedQuantity(Math.max(outstanding, 1) - outstanding);
        assignment.setExpirationDate(expiration);
        return assignment;
    }

    private static InventoryItem item() {
        InventoryItem item = new InventoryItem();
        item.setId(42L);
        item.setInventoryNumber("LEG-0012");
        item.setName("Leggio pieghevole");
        item.setTotalQuantity(5);
        item.setConditionStatus(InventoryCondition.FAIR);
        item.setQrPublicId(CODE);
        return item;
    }

    private static JwtAuthenticationToken authentication(String subject, String role) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("token", now, now.plusSeconds(60), Map.of("alg", "none"), Map.of("sub", subject, "tenant", "tenant-a"));
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role)));
    }
}
