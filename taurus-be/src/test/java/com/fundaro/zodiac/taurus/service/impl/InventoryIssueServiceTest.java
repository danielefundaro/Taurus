package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssuePhoto;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueReport;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueSeverity;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryIssueStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.notification.NotificationAudienceType;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryIssuePhotoRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryIssueReportRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryIssueDtos.CreateRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryIssueDtos.TransitionRequest;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryIssueServiceTest {
    private final InventoryItemRepository items = mock(InventoryItemRepository.class);
    private final InventoryAssignmentRepository assignments = mock(InventoryAssignmentRepository.class);
    private final InventoryIssueReportRepository issues = mock(InventoryIssueReportRepository.class);
    private final InventoryIssuePhotoRepository photos = mock(InventoryIssuePhotoRepository.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final NotificationOutboxPublisher notifications = mock(NotificationOutboxPublisher.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final InventoryQrCodeService qrCodes = mock(InventoryQrCodeService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final InventoryIssueService service = new InventoryIssueService(items, assignments, issues, photos, mediaService,
        mock(MediaRepository.class), notifications, inventoryService, qrCodes, new InventoryQrMetrics(registry));
    private final InventoryItem item = item();

    @BeforeEach
    void setUp() {
        when(items.findForUpdate(42L)).thenReturn(Optional.of(item));
        when(issues.save(any())).thenAnswer(invocation -> {
            InventoryIssueReport issue = invocation.getArgument(0);
            if (issue.getId() == null) issue.setId(900L);
            return issue;
        });
        when(issues.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void personalIssueNotifiesTheAdministratorsWithTheQuantityAndWithoutTheAuthor() {
        InventoryAssignment assignment = assignment("member-1", 3);
        when(assignments.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(assignment));
        when(assignments.findForUpdate(7L)).thenReturn(Optional.of(assignment));

        var response = service.createOwn(7L, new CreateRequest(2, InventoryIssueSeverity.UNSAFE, "  Asta piegata  "), authentication("member-1"));

        assertThat(response.description()).isEqualTo("Asta piegata");
        assertThat(response.assignmentId()).isEqualTo(7L);
        NotificationCommand command = singleNotification();
        assertThat(command.eventKey()).isEqualTo("inventory-issue:900:created:admins");
        assertThat(command.message()).isEqualTo("LEG-0012 · non sicuro · quantità 2");
        assertThat(command.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(command.targetPath()).isEqualTo("/inventory/items/42");
        assertThat(command.audiences()).contains(NotificationAudience.excludedUser("member-1"));
        assertThat(command.audiences()).extracting(NotificationAudience::type)
            .containsOnly(NotificationAudienceType.ROLE, NotificationAudienceType.EXCLUDED_USER);
        assertThat(registry.get("taurus.inventory.issue.created").tag("severity", "unsafe").counter().count()).isEqualTo(1);
    }

    @Test
    void administrativeIssueExcludesTheAdministratorWhoReportsIt() {
        service.createAdmin(42L, new CreateRequest(1, InventoryIssueSeverity.MINOR, "Graffio"), authentication("admin-1"));

        NotificationCommand command = singleNotification();
        assertThat(command.audiences()).contains(NotificationAudience.excludedUser("admin-1"));
        assertThat(command.message()).isEqualTo("LEG-0012 · minore · quantità 1");
        assertThat(command.severity()).isEqualTo(NotificationSeverity.INFO);
    }

    @Test
    void reportedQuantityCannotExceedTheItemOrTheOutstandingQuantity() {
        assertThatThrownBy(() -> service.createAdmin(42L, new CreateRequest(10, InventoryIssueSeverity.MINOR, "Rotto"), authentication("admin-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> {
                assertThat(error.getErrorKey()).isEqualTo("inventory.issue.quantityExceeded");
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
        InventoryAssignment assignment = assignment("member-1", 1);
        when(assignments.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(assignment));
        when(assignments.findForUpdate(7L)).thenReturn(Optional.of(assignment));
        assertThatThrownBy(() -> service.createOwn(7L, new CreateRequest(2, InventoryIssueSeverity.MINOR, "Rotto"), authentication("member-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("inventory.issue.quantityExceeded"));
        verifyNoInteractions(notifications);
    }

    @Test
    void anotherMembersAssignmentLooksMissing() {
        when(assignments.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(assignment("member-2", 3)));

        assertThatThrownBy(() -> service.createOwn(7L, new CreateRequest(1, InventoryIssueSeverity.MINOR, "Rotto"), authentication("member-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.findOwnForAssignment(7L, authentication("member-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("inventory.issue.notFound"));
    }

    @Test
    void listsTheIssuesOfAnOwnAssignment() {
        InventoryAssignment assignment = assignment("member-1", 3);
        when(assignments.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(assignment));
        when(issues.findAllByAssignment_IdAndDeletedFalseOrderByInsertDateDesc(7L)).thenReturn(List.of(issue(assignment, "member-1")));

        assertThat(service.findOwnForAssignment(7L, authentication("member-1")))
            .singleElement().satisfies(response -> assertThat(response.assignmentId()).isEqualTo(7L));
    }

    @Test
    void acknowledgementReachesOnlyTheMemberWhoReported() {
        InventoryIssueReport issue = issue(assignment("member-1", 3), "member-1");
        when(issues.findForUpdate(900L)).thenReturn(Optional.of(issue));

        service.transition(900L, new TransitionRequest(InventoryIssueStatus.ACKNOWLEDGED, null, null, 0), authentication("admin-1"));

        NotificationCommand command = singleNotification();
        assertThat(command.eventKey()).isEqualTo("inventory-issue:900:ACKNOWLEDGED:user:member-1");
        assertThat(command.audiences()).containsExactly(NotificationAudience.user("member-1"));
        assertThat(command.targetPath()).isEqualTo("/inventory/assignments/7");
        assertThat(command.message()).isEqualTo("LEG-0012 · aperta → presa in carico");
        assertThat(issue.getAcknowledgedBy()).isEqualTo("admin-1");
        assertThat(registry.get("taurus.inventory.issue.transition").tags("from", "open", "to", "acknowledged").counter().count()).isEqualTo(1);
    }

    @Test
    void acknowledgingOnesOwnAdministrativeIssueNotifiesNobody() {
        when(issues.findForUpdate(900L)).thenReturn(Optional.of(issue(null, "admin-1")));

        service.transition(900L, new TransitionRequest(InventoryIssueStatus.ACKNOWLEDGED, null, null, 0), authentication("admin-1"));

        verifyNoInteractions(notifications);
    }

    @Test
    void acknowledgingAnotherAdministratorsIssueNotifiesThatAdministrator() {
        when(issues.findForUpdate(900L)).thenReturn(Optional.of(issue(null, "admin-2")));

        service.transition(900L, new TransitionRequest(InventoryIssueStatus.ACKNOWLEDGED, null, null, 0), authentication("admin-1"));

        NotificationCommand command = singleNotification();
        assertThat(command.eventKey()).isEqualTo("inventory-issue:900:ACKNOWLEDGED:user:admin-2");
        assertThat(command.targetPath()).isEqualTo("/inventory/items/42");
    }

    @Test
    void resolutionReachesTheOtherAdministratorsAndTheReporterWithSeparateKeys() {
        when(issues.findForUpdate(900L)).thenReturn(Optional.of(issue(assignment("member-1", 3), "member-1")));

        service.transition(900L, new TransitionRequest(InventoryIssueStatus.RESOLVED, " Vite sostituita ", InventoryCondition.GOOD, 0), authentication("admin-1"));

        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notifications, times(2)).enqueue(captor.capture());
        assertThat(captor.getAllValues()).extracting(NotificationCommand::eventKey)
            .containsExactly("inventory-issue:900:RESOLVED:admins", "inventory-issue:900:RESOLVED:user:member-1");
        assertThat(captor.getAllValues().get(0).audiences()).contains(NotificationAudience.excludedUser("admin-1"));
        assertThat(captor.getAllValues().get(1).targetPath()).isEqualTo("/inventory/assignments/7");
        verify(inventoryService).updateConditionFromIssue(item, InventoryCondition.GOOD, "admin-1");
    }

    @Test
    void dismissingAnAdministrativeIssueNotifiesOnlyTheAdministrators() {
        when(issues.findForUpdate(900L)).thenReturn(Optional.of(issue(null, "admin-2")));

        service.transition(900L, new TransitionRequest(InventoryIssueStatus.DISMISSED, "Duplicata", null, 0), authentication("admin-1"));

        assertThat(singleNotification().eventKey()).isEqualTo("inventory-issue:900:DISMISSED:admins");
        verifyNoInteractions(inventoryService);
    }

    @Test
    void closingRequiresNotesAFreshVersionAndAnOpenIssue() {
        InventoryIssueReport issue = issue(null, "admin-2");
        when(issues.findForUpdate(900L)).thenReturn(Optional.of(issue));

        assertThatThrownBy(() -> service.transition(900L, new TransitionRequest(InventoryIssueStatus.RESOLVED, "  ", null, 0), authentication("admin-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("inventory.issue.resolutionNotes"));
        assertThatThrownBy(() -> service.transition(900L, new TransitionRequest(InventoryIssueStatus.RESOLVED, "Fatto", null, 3), authentication("admin-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> {
                assertThat(error.getErrorKey()).isEqualTo("inventory.issue.versionConflict");
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            });
        issue.setStatus(InventoryIssueStatus.RESOLVED);
        assertThatThrownBy(() -> service.transition(900L, new TransitionRequest(InventoryIssueStatus.DISMISSED, "Fatto", null, 0), authentication("admin-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("inventory.issue.transition"));
        issue.setStatus(InventoryIssueStatus.ACKNOWLEDGED);
        assertThatThrownBy(() -> service.transition(900L, new TransitionRequest(InventoryIssueStatus.OPEN, null, null, 0), authentication("admin-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("inventory.issue.transition"));
        verifyNoInteractions(notifications);
    }

    @Test
    void erasingAPhotoKeepsTheIssueAndReleasesTheStoredFile() {
        InventoryIssuePhoto photo = new InventoryIssuePhoto();
        photo.setId(55L);
        photo.setIssue(issue(null, "admin-2"));
        Media media = new Media();
        media.setId(77L);
        photo.setMediaAsset(media);
        when(photos.findByIdAndDeletedFalse(55L)).thenReturn(Optional.of(photo));

        service.erasePhoto(55L, authentication("admin-1"));

        assertThat(photo.isDeleted()).isTrue();
        assertThat(photo.getEditBy()).isEqualTo("admin-1");
        verify(photos).saveAndFlush(photo);
        verify(mediaService).deleteIfUnreferenced(77L, "tenant-a", "admin-1");
        assertThat(registry.get("taurus.inventory.issue.photo.erased").counter().count()).isEqualTo(1);
    }

    @Test
    void aMemberAddsPhotosOnlyToOwnOpenIssues() {
        MockMultipartFile file = new MockMultipartFile("file", "asta.jpg", "image/jpeg", new byte[] {1});
        when(issues.findByIdAndAssignment_UserKeycloakIdAndDeletedFalse(900L, "member-2")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addPhoto(900L, file, true, authentication("member-2")))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        InventoryIssueReport closed = issue(assignment("member-1", 1), "member-1");
        closed.setStatus(InventoryIssueStatus.RESOLVED);
        when(issues.findByIdAndAssignment_UserKeycloakIdAndDeletedFalse(900L, "member-1")).thenReturn(Optional.of(closed));
        assertThatThrownBy(() -> service.addPhoto(900L, file, true, authentication("member-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("inventory.issue.closed"));

        verifyNoInteractions(mediaService);
        verify(photos, never()).save(any());
    }

    @Test
    void erasingAMissingPhotoAnswersNotFound() {
        when(photos.findByIdAndDeletedFalse(55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.erasePhoto(55L, authentication("admin-1")))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(mediaService);
    }

    private NotificationCommand singleNotification() {
        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notifications).enqueue(captor.capture());
        return captor.getValue();
    }

    private InventoryIssueReport issue(InventoryAssignment assignment, String reporter) {
        InventoryIssueReport issue = new InventoryIssueReport();
        issue.initializeAudit(reporter);
        issue.setId(900L);
        issue.setItem(item);
        issue.setAssignment(assignment);
        issue.setReportedQuantity(1);
        issue.setSeverity(InventoryIssueSeverity.LIMITING);
        issue.setDescription("Vite allentata");
        issue.setStatus(InventoryIssueStatus.OPEN);
        return issue;
    }

    private InventoryAssignment assignment(String user, int outstanding) {
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setId(7L);
        assignment.setItem(item);
        assignment.setUserKeycloakId(user);
        assignment.setAssignedQuantity(outstanding);
        return assignment;
    }

    private static InventoryItem item() {
        InventoryItem item = new InventoryItem();
        item.setId(42L);
        item.setInventoryNumber("LEG-0012");
        item.setName("Leggio pieghevole");
        item.setTotalQuantity(9);
        item.setConditionStatus(InventoryCondition.FAIR);
        return item;
    }

    private static JwtAuthenticationToken authentication(String subject) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt("token", now, now.plusSeconds(60), Map.of("alg", "none"),
            Map.of("sub", subject, "tenant", "tenant-a", "given_name", "Mario", "family_name", "Rossi"));
        return new JwtAuthenticationToken(jwt);
    }
}
