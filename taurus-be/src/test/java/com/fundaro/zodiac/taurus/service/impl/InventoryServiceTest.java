package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentRevision;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
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
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryItemRequest;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    @Mock InventorySearchProjector searchProjector;
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
            searchProjector,
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

    private JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", "tenant-a")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
