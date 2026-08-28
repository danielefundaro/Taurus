package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentRevision;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItemPhoto;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturn;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnPhoto;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentDecisionRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRevisionRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemPhotoRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnPhotoRepository;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryDecisionRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentScope;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryItemRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryPhotoOrderRequest;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock InventoryItemRepository itemRepository;
    @Mock InventoryItemPhotoRepository photoRepository;
    @Mock InventoryAssignmentRepository assignmentRepository;
    @Mock InventoryAssignmentRevisionRepository revisionRepository;
    @Mock InventoryAssignmentDecisionRepository decisionRepository;
    @Mock InventoryReturnRepository returnRepository;
    @Mock InventoryReturnPhotoRepository returnPhotoRepository;
    @Mock UsersService usersService;
    @Mock TenantStorageService storageService;
    @Mock NoticesService noticesService;

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(
            itemRepository,
            photoRepository,
            assignmentRepository,
            revisionRepository,
            decisionRepository,
            returnRepository,
            returnPhotoRepository,
            usersService,
            storageService,
            new ObjectMapper(),
            noticesService
        );
    }

    @Test
    void shouldNotReduceTotalBelowOutstandingQuantity() {
        InventoryItem item = item(1L);
        when(itemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));
        when(assignmentRepository.sumOutstanding(1L, InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES)).thenReturn(5L);

        InventoryItemRequest request = new InventoryItemRequest("INV-1", "Leggio", null, 4, BigDecimal.TEN, "EUR", InventoryCondition.GOOD, null);

        assertThatThrownBy(() -> service.updateItem(1L, request, authentication()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("quantità totale");
    }

    @Test
    void shouldCheckOnlyActiveItemsWhenCreating() {
        InventoryItemRequest request = new InventoryItemRequest("INV-1", "Leggio", null, 1, BigDecimal.TEN, "EUR", InventoryCondition.GOOD, null);
        when(itemRepository.save(any(InventoryItem.class)))
            .thenAnswer(invocation -> {
                InventoryItem item = invocation.getArgument(0);
                item.setId(1L);
                return item;
            });

        service.createItem(request, authentication());

        verify(itemRepository).existsByInventoryNumberIgnoreCaseAndDeletedFalse("INV-1");
    }

    @Test
    void shouldPageOnlyAssignmentsOwnedByAuthenticatedUser() {
        Pageable pageable = PageRequest.of(0, 10);
        when(assignmentRepository.findAllByUserKeycloakIdAndDeletedFalseAndStatusIn(eq("user-1"), eq(InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES), eq(pageable)))
            .thenReturn(Page.empty(pageable));

        service.findOwnAssignments(null, InventoryAssignmentScope.POSSESSED, pageable, authentication());

        verify(assignmentRepository).findAllByUserKeycloakIdAndDeletedFalseAndStatusIn("user-1", InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES, pageable);
    }

    @Test
    void shouldUseTextSearchOnlyWithANonBlankQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        when(assignmentRepository.searchOwn("user-1", "leggio", InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES, pageable))
            .thenReturn(Page.empty(pageable));

        service.findOwnAssignments("  leggio  ", InventoryAssignmentScope.POSSESSED, pageable, authentication());

        verify(assignmentRepository).searchOwn("user-1", "leggio", InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES, pageable);
    }

    @Test
    void shouldBuildAdminDashboardSummaryFromAggregates() {
        when(itemRepository.countByDeletedFalse()).thenReturn(4L);
        when(itemRepository.sumTotalQuantity()).thenReturn(10L);
        when(assignmentRepository.sumOutstanding(InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES)).thenReturn(3L);
        when(decisionRepository.countPendingCurrentRevisions(InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES)).thenReturn(2L);
        when(returnRepository.countByDeletedFalseAndStatus(InventoryReturnStatus.REQUESTED)).thenReturn(1L);

        var summary = service.getAdminSummary(authentication());

        assertThat(summary.registeredItems()).isEqualTo(4L);
        assertThat(summary.totalQuantity()).isEqualTo(10L);
        assertThat(summary.assignedQuantity()).isEqualTo(3L);
        assertThat(summary.availableQuantity()).isEqualTo(7L);
        assertThat(summary.pendingDecisions()).isEqualTo(2L);
        assertThat(summary.pendingReturns()).isEqualTo(1L);
    }

    @Test
    void shouldBuildPersonalDashboardSummaryForAuthenticatedUser() {
        ZonedDateTime lastAssignedAt = ZonedDateTime.now().minusDays(1);
        when(assignmentRepository.countByUserKeycloakIdAndDeletedFalseAndStatusIn("user-1", InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES))
            .thenReturn(2L);
        when(assignmentRepository.sumOutstandingForUser("user-1", InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES)).thenReturn(3L);
        when(decisionRepository.countPendingCurrentRevisionsForUser("user-1", InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES)).thenReturn(1L);
        when(assignmentRepository.findLatestAssignedAtForUser("user-1", InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES)).thenReturn(lastAssignedAt);

        var summary = service.getOwnSummary(authentication());

        assertThat(summary.possessedItems()).isEqualTo(2L);
        assertThat(summary.outstandingQuantity()).isEqualTo(3L);
        assertThat(summary.pendingDecisions()).isEqualTo(1L);
        assertThat(summary.lastAssignedAt()).isEqualTo(lastAssignedAt);
    }

    @Test
    void shouldNotExposeAssignmentOwnedByAnotherUser() {
        when(assignmentRepository.findByIdAndUserKeycloakIdAndDeletedFalse(2L, "user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOwnAssignment(2L, authentication()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("Assegnazione non trovata");
    }

    @Test
    void shouldRequireReasonWhenUserRejectsCurrentRevision() {
        InventoryItem item = item(1L);
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setId(2L);
        assignment.setDeleted(false);
        assignment.setItem(item);
        assignment.setUserKeycloakId("user-1");
        assignment.setAssignedQuantity(1);
        assignment.setReturnedQuantity(0);
        assignment.setStatus(InventoryAssignmentStatus.ACTIVE);
        assignment.setCurrentRevision(1);
        InventoryAssignmentRevision revision = new InventoryAssignmentRevision();
        revision.setSnapshotHash("a".repeat(64));

        when(assignmentRepository.findForUpdate(2L)).thenReturn(Optional.of(assignment));
        when(revisionRepository.findByAssignment_IdAndRevisionNumber(2L, 1)).thenReturn(Optional.of(revision));
        when(decisionRepository.findByRevision_Id(null)).thenReturn(Optional.empty());

        InventoryDecisionRequest request = new InventoryDecisionRequest(InventoryDecisionType.REJECTED, "  ", "a".repeat(64));

        assertThatThrownBy(() -> service.decide(2L, request, authentication()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("motivazione");
    }

    @Test
    void shouldPersistTheRequestedPhotoOrder() {
        InventoryItem item = item(1L);
        InventoryItemPhoto first = photo(10L, item, 0, true);
        InventoryItemPhoto second = photo(20L, item, 1, false);
        when(itemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));
        when(photoRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(1L)).thenReturn(List.of(first, second));

        var result = service.reorderPhotos(1L, new InventoryPhotoOrderRequest(List.of(20L, 10L)), authentication());

        assertThat(result).extracting(value -> value.id()).containsExactly(20L, 10L);
        assertThat(second.getDisplayOrder()).isZero();
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        verify(photoRepository).saveAll(List.of(second, first));
    }

    @Test
    void shouldRejectAnIncompletePhotoOrder() {
        InventoryItem item = item(1L);
        InventoryItemPhoto first = photo(10L, item, 0, true);
        InventoryItemPhoto second = photo(20L, item, 1, false);
        when(itemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));
        when(photoRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(1L)).thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.reorderPhotos(1L, new InventoryPhotoOrderRequest(List.of(10L)), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("tutte e sole");
    }

    @Test
    void shouldSelectExactlyOnePreviewPhoto() {
        InventoryItem item = item(1L);
        InventoryItemPhoto first = photo(10L, item, 0, true);
        InventoryItemPhoto second = photo(20L, item, 1, false);
        when(itemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));
        when(photoRepository.findByIdAndItem_IdAndDeletedFalse(20L, 1L)).thenReturn(Optional.of(second));
        when(photoRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(1L)).thenReturn(List.of(first, second));

        var result = service.setPreviewPhoto(1L, 20L, authentication());

        assertThat(first.isPreview()).isFalse();
        assertThat(second.isPreview()).isTrue();
        assertThat(result).extracting(value -> value.preview()).containsExactly(false, true);
        verify(photoRepository).saveAll(List.of(first, second));
    }

    @Test
    void shouldSoftDeleteAssignmentAndItsReturns() {
        InventoryItem item = item(1L);
        InventoryAssignment assignment = assignment(2L, item, 2, 0, InventoryAssignmentStatus.ACTIVE);
        InventoryReturn inventoryReturn = inventoryReturn(3L, assignment, 1, InventoryReturnStatus.REQUESTED);
        InventoryReturnPhoto photo = new InventoryReturnPhoto();
        photo.setId(4L);
        photo.setInventoryReturn(inventoryReturn);
        when(assignmentRepository.findForUpdate(2L)).thenReturn(Optional.of(assignment));
        when(returnRepository.findAllByAssignment_IdAndDeletedFalseOrderByRequestedAtDesc(2L)).thenReturn(List.of(inventoryReturn));
        when(returnPhotoRepository.findAllByInventoryReturn_IdAndDeletedFalseOrderByIdAsc(3L)).thenReturn(List.of(photo));

        service.deleteAssignment(2L, authentication());

        assertThat(assignment.isDeleted()).isTrue();
        assertThat(inventoryReturn.isDeleted()).isTrue();
        assertThat(photo.isDeleted()).isTrue();
        verify(assignmentRepository).save(assignment);
        verify(returnRepository).save(inventoryReturn);
        verify(returnPhotoRepository).saveAll(List.of(photo));
    }

    @Test
    void shouldRestoreAssignmentQuantityWhenDeletingACompletedReturn() {
        InventoryItem item = item(1L);
        InventoryAssignment assignment = assignment(2L, item, 5, 2, InventoryAssignmentStatus.PARTIALLY_RETURNED);
        InventoryReturn inventoryReturn = inventoryReturn(3L, assignment, 2, InventoryReturnStatus.COMPLETED);
        when(returnRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(inventoryReturn));
        when(returnRepository.findForUpdate(3L)).thenReturn(Optional.of(inventoryReturn));
        when(assignmentRepository.findForUpdate(2L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.sumOutstanding(1L, InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES)).thenReturn(3L);

        service.deleteReturn(3L, authentication());

        assertThat(assignment.getReturnedQuantity()).isZero();
        assertThat(assignment.getStatus()).isEqualTo(InventoryAssignmentStatus.ACTIVE);
        assertThat(inventoryReturn.isDeleted()).isTrue();
        verify(assignmentRepository).save(assignment);
        verify(returnRepository).save(inventoryReturn);
    }

    @Test
    void shouldNotDeleteACompletedReturnWhenItsMaterialWasReassigned() {
        InventoryItem item = item(1L);
        item.setTotalQuantity(5);
        InventoryAssignment assignment = assignment(2L, item, 5, 2, InventoryAssignmentStatus.PARTIALLY_RETURNED);
        InventoryReturn inventoryReturn = inventoryReturn(3L, assignment, 2, InventoryReturnStatus.COMPLETED);
        when(returnRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(inventoryReturn));
        when(returnRepository.findForUpdate(3L)).thenReturn(Optional.of(inventoryReturn));
        when(assignmentRepository.findForUpdate(2L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.sumOutstanding(1L, InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES)).thenReturn(4L);

        assertThatThrownBy(() -> service.deleteReturn(3L, authentication()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("già stato riassegnato");

        assertThat(inventoryReturn.isDeleted()).isFalse();
        assertThat(assignment.getReturnedQuantity()).isEqualTo(2);
    }

    private InventoryItem item(Long id) {
        InventoryItem item = new InventoryItem();
        item.setId(id);
        item.setDeleted(false);
        item.setInventoryNumber("INV-1");
        item.setName("Leggio");
        item.setTotalQuantity(10);
        item.setConditionStatus(InventoryCondition.GOOD);
        item.setInsertDate(ZonedDateTime.now());
        item.setInsertBy("admin");
        item.setEditDate(ZonedDateTime.now());
        item.setEditBy("admin");
        return item;
    }

    private InventoryItemPhoto photo(Long id, InventoryItem item, int displayOrder, boolean preview) {
        InventoryItemPhoto photo = new InventoryItemPhoto();
        photo.setId(id);
        photo.setItem(item);
        photo.setDisplayOrder(displayOrder);
        photo.setPreview(preview);
        photo.setFileName("photo-" + id + ".jpg");
        photo.setContentType("image/jpeg");
        photo.setFileSize(100);
        photo.setInsertDate(ZonedDateTime.now());
        return photo;
    }

    private InventoryAssignment assignment(
        Long id,
        InventoryItem item,
        int assignedQuantity,
        int returnedQuantity,
        InventoryAssignmentStatus status
    ) {
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setId(id);
        assignment.setItem(item);
        assignment.setAssignedQuantity(assignedQuantity);
        assignment.setReturnedQuantity(returnedQuantity);
        assignment.setStatus(status);
        assignment.setDeleted(false);
        return assignment;
    }

    private InventoryReturn inventoryReturn(
        Long id,
        InventoryAssignment assignment,
        int quantity,
        InventoryReturnStatus status
    ) {
        InventoryReturn inventoryReturn = new InventoryReturn();
        inventoryReturn.setId(id);
        inventoryReturn.setAssignment(assignment);
        inventoryReturn.setQuantity(quantity);
        inventoryReturn.setStatus(status);
        inventoryReturn.setDeleted(false);
        return inventoryReturn;
    }

    private JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", "tenant-a")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
