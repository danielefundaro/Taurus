package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.aop.notices.NoticesAspect;
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
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.enumeration.MediaAssetStatus;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentDecisionRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRevisionRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemPhotoRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryReturnPhotoRepository;
import com.fundaro.zodiac.taurus.service.AlbumsService;
import com.fundaro.zodiac.taurus.service.CalendarEventsService;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryDecisionRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentScope;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryItemRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryPhotoOrderRequest;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.mock.web.MockMultipartFile;

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
    @Mock MediaService mediaService;
    @Mock MediaRepository mediaRepository;
    @Mock NotificationOutboxPublisher notificationPublisher;
    @Mock TenantsService tenantsService;
    @Mock InstrumentsService instrumentsService;
    @Mock AlbumsService albumsService;
    @Mock TracksService tracksService;
    @Mock CalendarEventsService calendarEventsService;
    @Mock UsersRepository usersRepository;
    @Mock KeycloakService keycloakService;

    private InventoryService service;

    @BeforeEach
    void setUp() {
        InventoryNoticeDataService noticeDataService = new InventoryNoticeDataService(
            itemRepository,
            assignmentRepository,
            photoRepository,
            returnRepository,
            usersRepository,
            keycloakService
        );
        NoticesAspect noticesAspect = new NoticesAspect(
            notificationPublisher,
            null,
            usersService,
            tenantsService,
            instrumentsService,
            albumsService,
            tracksService,
            calendarEventsService,
            noticeDataService,
            null
        );
        AspectJProxyFactory revisionProxyFactory = new AspectJProxyFactory(revisionRepository);
        revisionProxyFactory.addAspect(noticesAspect);
        InventoryAssignmentRevisionRepository advisedRevisionRepository = revisionProxyFactory.getProxy();
        InventoryService target = new InventoryService(
            itemRepository,
            photoRepository,
            assignmentRepository,
            advisedRevisionRepository,
            decisionRepository,
            returnRepository,
            returnPhotoRepository,
            usersService,
            mediaService,
            mediaRepository,
            new ObjectMapper()
        );
        AspectJProxyFactory serviceProxyFactory = new AspectJProxyFactory(target);
        serviceProxyFactory.addAspect(noticesAspect);
        service = serviceProxyFactory.getProxy();
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

        var result = service.createItem(request, authentication());

        verify(itemRepository).existsByInventoryNumberIgnoreCaseAndDeletedFalse("INV-1");
        verifyNotification("Inventario: oggetto creato", "user-1 ha creato l'oggetto “INV-1 — Leggio”");
    }

    @Test
    void shouldDefaultMissingEstimatedUnitValueToZero() {
        InventoryItemRequest request = new InventoryItemRequest("INV-1", "Leggio", null, 1, null, "EUR", InventoryCondition.GOOD, null);
        when(itemRepository.save(any(InventoryItem.class)))
            .thenAnswer(invocation -> {
                InventoryItem item = invocation.getArgument(0);
                item.setId(1L);
                return item;
            });

        var result = service.createItem(request, authentication());

        assertThat(result.estimatedUnitValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.currency()).isEqualTo("EUR");
        verify(itemRepository).save(argThat(item -> BigDecimal.ZERO.compareTo(item.getEstimatedUnitValue()) == 0));
    }

    @Test
    void shouldNotifyAdminsWhenAnItemIsModified() {
        InventoryItem item = item(1L);
        when(itemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));

        service.updateItem(
            1L,
            new InventoryItemRequest("INV-1", "Leggio aggiornato", null, 10, BigDecimal.TEN, "EUR", InventoryCondition.GOOD, null),
            authentication()
        );

        verifyNotification("Inventario: oggetto aggiornato", "ha aggiornato l'oggetto “INV-1 — Leggio aggiornato”");
    }

    @Test
    void shouldNotifyAdminsWhenAnItemIsRemoved() {
        InventoryItem item = item(1L);
        when(itemRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(item));
        when(itemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));

        service.deleteItem(1L, authentication());

        assertThat(item.isDeleted()).isTrue();
        verifyNotification("Inventario: oggetto rimosso", "ha rimosso l'oggetto “INV-1 — Leggio”");
    }

    @Test
    void shouldNotifyAdminsWhenAnItemIsAssigned() {
        InventoryItem item = item(1L);
        UsersDTO user = new UsersDTO();
        user.setId(42L);
        user.setKeycloakId("assigned-user");
        user.setName("Mario");
        user.setLastName("Rossi");
        AtomicReference<InventoryAssignmentRevision> savedRevision = new AtomicReference<>();

        when(itemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));
        when(usersService.findOne(eq(42L), any(JwtAuthenticationToken.class))).thenReturn(Optional.of(user));
        when(assignmentRepository.save(any(InventoryAssignment.class))).thenAnswer(invocation -> {
            InventoryAssignment assignment = invocation.getArgument(0);
            if (assignment.getId() == null) assignment.setId(2L);
            return assignment;
        });
        when(revisionRepository.save(any(InventoryAssignmentRevision.class))).thenAnswer(invocation -> {
            InventoryAssignmentRevision revision = invocation.getArgument(0);
            revision.setId(3L);
            savedRevision.set(revision);
            return revision;
        });
        when(revisionRepository.findByAssignment_IdAndRevisionNumber(2L, 1))
            .thenAnswer(invocation -> Optional.ofNullable(savedRevision.get()));

        service.assign(1L, new InventoryAssignmentRequest(42L, 0, 2, null, null), authentication());

        verifyNotification("Inventario: oggetto assegnato", "ha assegnato 2 unità dell'oggetto “INV-1 — Leggio” a Mario Rossi");
        verifyNotification("Inventario: presa visione richiesta", "revisione 1 della tua assegnazione dell'oggetto “INV-1 — Leggio”");
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
    void shouldStoreExpirationOnTheAssignmentAndCreateANewRevision() {
        InventoryItem item = item(1L);
        InventoryAssignment assignment = assignment(2L, item, 1, 0, InventoryAssignmentStatus.ACTIVE);
        assignment.setUserIndex(42L);
        assignment.setCurrentRevision(0);
        InventoryAssignmentRevision revision = new InventoryAssignmentRevision();
        revision.setRevisionNumber(1);
        revision.setSnapshotHash("a".repeat(64));
        LocalDate expirationDate = LocalDate.of(2027, 6, 30);

        when(assignmentRepository.findForUpdate(2L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.sumOutstanding(1L, InventoryService.OUTSTANDING_ASSIGNMENT_STATUSES)).thenReturn(1L);
        when(revisionRepository.findByAssignment_IdAndRevisionNumber(2L, 1)).thenReturn(Optional.of(revision));

        var result = service.updateAssignment(
            2L,
            new InventoryAssignmentRequest(42L, 0, 1, null, expirationDate),
            authentication()
        );

        assertThat(assignment.getExpirationDate()).isEqualTo(expirationDate);
        assertThat(result.expirationDate()).isEqualTo(expirationDate);
        assertThat(assignment.getCurrentRevision()).isEqualTo(1);
        verify(revisionRepository).save(any(InventoryAssignmentRevision.class));
        verifyNotification("Inventario: assegnazione aggiornata", "ha aggiornato l'assegnazione dell'oggetto “INV-1 — Leggio”");
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
    void shouldNotifyAdminsWhenTheUserAcceptsTheAcknowledgement() {
        InventoryAssignment assignment = decisionAssignment();
        InventoryAssignmentRevision revision = revision(1);
        when(assignmentRepository.findForUpdate(2L)).thenReturn(Optional.of(assignment));
        when(revisionRepository.findByAssignment_IdAndRevisionNumber(2L, 1)).thenReturn(Optional.of(revision));
        when(decisionRepository.findByRevision_Id(3L)).thenReturn(Optional.empty());

        service.decide(
            2L,
            new InventoryDecisionRequest(InventoryDecisionType.ACCEPTED, null, "a".repeat(64)),
            authentication()
        );

        verifyNotification("Inventario: presa visione accettata", "Mario Rossi ha accettato la revisione 1 dell'assegnazione dell'oggetto “INV-1 — Leggio”");
    }

    @Test
    void shouldNotifyAdminsWhenTheUserRejectsTheAcknowledgement() {
        InventoryAssignment assignment = decisionAssignment();
        InventoryAssignmentRevision revision = revision(1);
        when(assignmentRepository.findForUpdate(2L)).thenReturn(Optional.of(assignment));
        when(revisionRepository.findByAssignment_IdAndRevisionNumber(2L, 1)).thenReturn(Optional.of(revision));
        when(decisionRepository.findByRevision_Id(3L)).thenReturn(Optional.empty());

        service.decide(
            2L,
            new InventoryDecisionRequest(InventoryDecisionType.REJECTED, "Quantità errata", "a".repeat(64)),
            authentication()
        );

        verifyNotification("Inventario: presa visione rifiutata", "Motivazione: Quantità errata");
    }

    @Test
    void shouldNotifyAdminsWhenAPhotoIsAdded() throws IOException {
        InventoryItem item = item(1L);
        MediaDTO storedMedia = new MediaDTO();
        storedMedia.setId(50L);
        storedMedia.setOriginalFilename("front.png");
        Media media = new Media();
        media.setId(50L);
        media.setOriginalFilename("front.png");
        media.setMimeType("image/png");
        media.setFileExtension("png");
        media.setFileSize(100);
        media.setSha256("a".repeat(64));
        media.setStatus(MediaAssetStatus.READY);
        MockMultipartFile file = new MockMultipartFile("file", "front.png", "image/png", png());
        when(itemRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(item));
        when(itemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));
        when(mediaService.store(any(byte[].class), eq("front.png"), eq("image/png"), eq("inventory"), any(JwtAuthenticationToken.class)))
            .thenReturn(storedMedia);
        when(mediaRepository.getReferenceById(50L)).thenReturn(media);
        when(photoRepository.save(any(InventoryItemPhoto.class))).thenAnswer(invocation -> {
            InventoryItemPhoto photo = invocation.getArgument(0);
            photo.setId(10L);
            return photo;
        });

        service.addPhoto(1L, file, authentication());

        verifyNotification("Inventario: fotografia aggiunta", "ha aggiunto la fotografia “front.png” all'oggetto “INV-1 — Leggio”");
    }

    @Test
    void shouldPersistTheRequestedPhotoOrder() {
        InventoryItem item = item(1L);
        InventoryItemPhoto first = photo(10L, item, 0, true);
        InventoryItemPhoto second = photo(20L, item, 1, false);
        when(itemRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(item));
        when(itemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));
        when(photoRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(1L)).thenReturn(List.of(first, second));

        var result = service.reorderPhotos(1L, new InventoryPhotoOrderRequest(List.of(20L, 10L)), authentication());

        assertThat(result).extracting(value -> value.id()).containsExactly(20L, 10L);
        assertThat(second.getDisplayOrder()).isZero();
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        verify(photoRepository).saveAll(List.of(second, first));
        verifyNotification("Inventario: fotografie aggiornate", "ha aggiornato l'ordine delle fotografie dell'oggetto “INV-1 — Leggio”");
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
        when(itemRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(item));
        when(itemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));
        when(photoRepository.findByIdAndItem_IdAndDeletedFalse(20L, 1L)).thenReturn(Optional.of(second));
        when(photoRepository.findAllByItem_IdAndDeletedFalseOrderByDisplayOrderAsc(1L)).thenReturn(List.of(first, second));

        var result = service.setPreviewPhoto(1L, 20L, authentication());

        assertThat(first.isPreview()).isFalse();
        assertThat(second.isPreview()).isTrue();
        assertThat(result).extracting(value -> value.preview()).containsExactly(false, true);
        verify(photoRepository).saveAll(List.of(first, second));
        verifyNotification("Inventario: fotografie aggiornate", "ha impostato “photo-20.jpg” come fotografia di anteprima dell'oggetto “INV-1 — Leggio”");
    }

    @Test
    void shouldNotifyAdminsWhenAPhotoIsRemoved() {
        InventoryItem item = item(1L);
        InventoryItemPhoto photo = photo(10L, item, 0, false);
        when(photoRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(photo));
        when(photoRepository.findNoticeTargetById(10L)).thenReturn(Optional.of(photo));
        when(itemRepository.findForUpdate(1L)).thenReturn(Optional.of(item));

        service.deletePhoto(10L, authentication());

        assertThat(photo.isDeleted()).isTrue();
        verifyNotification("Inventario: fotografia rimossa", "ha rimosso la fotografia “photo-10.jpg” dall'oggetto “INV-1 — Leggio”");
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
        when(assignmentRepository.findNoticeTargetById(2L)).thenReturn(Optional.of(assignment));
        when(returnRepository.findAllByAssignment_IdAndDeletedFalseOrderByRequestedAtDesc(2L)).thenReturn(List.of(inventoryReturn));
        when(returnPhotoRepository.findAllByInventoryReturn_IdAndDeletedFalseOrderByIdAsc(3L)).thenReturn(List.of(photo));

        service.deleteAssignment(2L, authentication());

        assertThat(assignment.isDeleted()).isTrue();
        assertThat(inventoryReturn.isDeleted()).isTrue();
        assertThat(photo.isDeleted()).isTrue();
        verify(assignmentRepository).save(assignment);
        verify(returnRepository).save(inventoryReturn);
        verify(returnPhotoRepository).saveAll(List.of(photo));
        verifyNotification("Inventario: assegnazione rimossa", "ha rimosso l'assegnazione dell'oggetto “INV-1 — Leggio”");
    }

    @Test
    void shouldNotifyTheUserAndAdminsWhenAReturnIsCompleted() {
        InventoryItem item = item(1L);
        InventoryAssignment assignment = assignment(2L, item, 2, 0, InventoryAssignmentStatus.ACTIVE);
        assignment.setUserKeycloakId("assigned-user");
        assignment.setUserName("Mario");
        assignment.setUserLastName("Rossi");
        InventoryReturn inventoryReturn = inventoryReturn(3L, assignment, 1, InventoryReturnStatus.REQUESTED);
        when(returnRepository.findNoticeTargetById(3L)).thenReturn(Optional.of(inventoryReturn));
        when(returnRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(inventoryReturn));
        when(returnRepository.findForUpdate(3L)).thenReturn(Optional.of(inventoryReturn));
        when(returnRepository.save(inventoryReturn)).thenReturn(inventoryReturn);
        when(assignmentRepository.findForUpdate(2L)).thenReturn(Optional.of(assignment));

        service.completeReturn(3L, new com.fundaro.zodiac.taurus.service.dto.inventory.InventoryReturnRequest(
            1,
            InventoryCondition.GOOD,
            null
        ), authentication());

        verifyNotification("Inventario: riconsegna completata", "1 unità dell'oggetto “INV-1 — Leggio” assegnato a te");
        verifyNotification("Inventario: riconsegna completata", "ha completato la riconsegna di 1 unità dell'oggetto “INV-1 — Leggio” assegnato a Mario Rossi");
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
        Media media = new Media();
        media.setId(1000L + id);
        media.setOriginalFilename("photo-" + id + ".jpg");
        media.setMimeType("image/jpeg");
        media.setFileExtension("jpg");
        media.setFileSize(100);
        media.setSha256("a".repeat(64));
        media.setStatus(MediaAssetStatus.READY);
        photo.setMediaAsset(media);
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

    private InventoryAssignment decisionAssignment() {
        InventoryAssignment assignment = assignment(2L, item(1L), 1, 0, InventoryAssignmentStatus.ACTIVE);
        assignment.setUserKeycloakId("user-1");
        assignment.setUserName("Mario");
        assignment.setUserLastName("Rossi");
        assignment.setCurrentRevision(1);
        return assignment;
    }

    private InventoryAssignmentRevision revision(int number) {
        InventoryAssignmentRevision revision = new InventoryAssignmentRevision();
        revision.setId(3L);
        revision.setRevisionNumber(number);
        revision.setSnapshotHash("a".repeat(64));
        return revision;
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

    private byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private void verifyNotification(String title, String messageFragment) {
        verify(notificationPublisher).enqueue(argThat(command ->
            title.equals(command.title()) && command.message().contains(messageFragment)
        ));
    }

    private JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", "tenant-a")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
