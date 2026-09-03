package com.fundaro.zodiac.taurus.aop.notices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentRevision;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.notification.NotificationAudienceType;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.service.AlbumsService;
import com.fundaro.zodiac.taurus.service.CalendarEventsService;
import com.fundaro.zodiac.taurus.service.InstrumentsService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.UsersService;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryItemDTO;
import com.fundaro.zodiac.taurus.service.impl.AlbumsServiceImpl;
import com.fundaro.zodiac.taurus.service.impl.CrossTenantNotificationPublisher;
import com.fundaro.zodiac.taurus.service.impl.FinanceNoticeDataService;
import com.fundaro.zodiac.taurus.service.impl.InventoryNoticeDataService;
import com.fundaro.zodiac.taurus.service.impl.NotificationOutboxPublisher;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NoticesAspectTest {

    @Mock NotificationOutboxPublisher publisher;
    @Mock CrossTenantNotificationPublisher crossTenantPublisher;
    @Mock UsersService usersService;
    @Mock TenantsService tenantsService;
    @Mock InstrumentsService instrumentsService;
    @Mock AlbumsService albumsService;
    @Mock TracksService tracksService;
    @Mock CalendarEventsService calendarEventsService;
    @Mock InventoryNoticeDataService inventoryNoticeDataService;
    @Mock FinanceNoticeDataService financeNoticeDataService;
    private NoticesAspect aspect;
    private JwtAuthenticationToken authentication;

    @BeforeEach
    void setUp() {
        aspect = new NoticesAspect(
            publisher,
            crossTenantPublisher,
            usersService,
            tenantsService,
            instrumentsService,
            albumsService,
            tracksService,
            calendarEventsService,
            inventoryNoticeDataService,
            financeNoticeDataService
        );
        authentication = authentication();
    }

    @Test
    void composesContentCreationWithCanonicalFields() throws Throwable {
        AlbumsDTO album = album(12L, "Concerto d'estate", StateEnum.PUBLIC);

        invoke("onSave", joinPoint("save", album, authentication));

        NotificationCommand command = capturePublisherCommand();
        assertCommand(
            command,
            NotificationSource.CONTENT,
            "ALBUM",
            null,
            "ALBUM_CREATO",
            "Album: creato",
            "Mario Rossi ha creato l'album “Concerto d'estate”.",
            NotificationSeverity.SUCCESS,
            "/albums",
            Set.of(NotificationAudience.allActiveUsers(), NotificationAudience.role(RoleEnum.ROLE_SUPER_ADMIN))
        );
        assertThat(command.eventKey()).startsWith("content:album:album_creato:");
    }

    @Test
    void composesContentUpdateAndRemovalFromTheCompletedOperation() throws Throwable {
        AlbumsDTO before = album(12L, "Concerto", StateEnum.DRAFT);
        AlbumsDTO after = album(12L, "Concerto", StateEnum.PUBLIC);
        AlbumsServiceImpl target = mock(AlbumsServiceImpl.class);
        when(albumsService.findOne(12L, authentication)).thenReturn(Optional.of(before));

        invoke("onUpdate", targetJoinPoint("update", after, target, 12L, after, authentication));
        NotificationCommand update = capturePublisherCommand();
        assertThat(update.title()).isEqualTo("Album: pubblicato");
        assertThat(update.operation()).isEqualTo("ALBUM_PUBBLICATO");
        assertThat(update.source()).isEqualTo(NotificationSource.CONTENT);
        assertThat(update.targetPath()).isEqualTo("/albums");
        assertThat(update.actorId()).isEqualTo("actor-1");
        assertThat(update.actorDisplayName()).isEqualTo("Mario Rossi");
        assertThat(update.audiences()).contains(NotificationAudience.role(RoleEnum.ROLE_USER), NotificationAudience.role(RoleEnum.ROLE_ARCHIVIST));

        org.mockito.Mockito.reset(publisher);
        invoke("onDelete", joinPoint("delete", after, 12L, authentication));
        NotificationCommand removal = capturePublisherCommand();
        assertThat(removal.title()).isEqualTo("Album: rimosso");
        assertThat(removal.message()).isEqualTo("Mario Rossi ha rimosso l'album “Concerto”.");
        assertThat(removal.severity()).isEqualTo(NotificationSeverity.WARNING);
        assertThat(removal.audiences()).contains(NotificationAudience.allActiveUsers());
        assertThat(removal.eventKey()).startsWith("content:album:album_rimosso:");
    }

    @Test
    void composesCalendarAvailability() throws Throwable {
        CalendarEventsDTO event = new CalendarEventsDTO();
        event.setId(23L);
        event.setName("Prova generale");

        invoke("onSetAvailability", joinPoint("setAvailability", event, 23L, true, authentication));

        NotificationCommand command = capturePublisherCommand();
        assertCommand(
            command,
            NotificationSource.CALENDAR,
            "EVENTO",
            null,
            "EVENTO_DISPONIBILITA_CONFERMATA",
            "Evento: disponibilità confermata",
            "Mario Rossi ha confermato la disponibilità per l'evento “Prova generale”.",
            NotificationSeverity.SUCCESS,
            "/calendar-events",
            Set.of(NotificationAudience.role(RoleEnum.ROLE_ADMIN), NotificationAudience.role(RoleEnum.ROLE_SUPER_ADMIN))
        );
    }

    @Test
    void composesIdentityNotification() throws Throwable {
        UsersDTO user = new UsersDTO();
        user.setId(31L);
        user.setName("Luisa");
        user.setLastName("Bianchi");

        invoke("onSave", joinPoint("save", user, authentication));

        NotificationCommand command = capturePublisherCommand();
        assertCommand(
            command,
            NotificationSource.IDENTITY,
            "UTENTE",
            null,
            "UTENTE_CREATO",
            "Utente: creato",
            "Mario Rossi ha creato l'utente Luisa Bianchi.",
            NotificationSeverity.SUCCESS,
            "/users",
            Set.of(NotificationAudience.role(RoleEnum.ROLE_ADMIN), NotificationAudience.role(RoleEnum.ROLE_SUPER_ADMIN))
        );
    }

    @Test
    void composesCrossTenantNotification() throws Throwable {
        TenantsDTO tenant = new TenantsDTO();
        tenant.setId(41L);
        tenant.setName("Associazione Aurora");
        tenant.setCode("AURORA");

        invoke("onSave", joinPoint("save", tenant, authentication));

        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(crossTenantPublisher).enqueue(captor.capture());
        NotificationCommand command = captor.getValue();
        assertThat(command.source()).isEqualTo(NotificationSource.TENANT);
        assertThat(command.aggregateType()).isEqualTo("TENANT");
        assertThat(command.aggregateId()).isEqualTo("AURORA");
        assertThat(command.operation()).isEqualTo("TENANT_CREATO");
        assertThat(command.title()).isEqualTo("Tenant: creato");
        assertThat(command.message()).isEqualTo("Mario Rossi ha creato il tenant “Associazione Aurora” con codice “AURORA”.");
        assertThat(command.targetPath()).isNull();
        assertThat(command.actorId()).isEqualTo("actor-1");
        assertThat(command.actorDisplayName()).isEqualTo("Mario Rossi");
        assertThat(command.audiences()).containsExactly(NotificationAudience.role(RoleEnum.ROLE_SUPER_ADMIN));
        assertThat(command.targetTenantCode()).isEqualTo("AURORA");
        assertThat(command.eventKey()).startsWith("tenant:tenant:tenant_creato:");
    }

    @Test
    void composesInventoryCreation() throws Throwable {
        InventoryItemDTO item = new InventoryItemDTO(
            51L,
            "INV-51",
            "Leggio",
            null,
            4,
            0,
            4,
            null,
            "EUR",
            InventoryCondition.GOOD,
            null,
            0,
            List.of(),
            List.of()
        );

        invoke("onInventoryChange", joinPoint("createItem", item, new Object(), authentication));

        NotificationCommand command = capturePublisherCommand();
        assertCommand(
            command,
            NotificationSource.INVENTORY,
            "INVENTARIO",
            null,
            "INVENTARIO_OGGETTO_CREATO",
            "Inventario: oggetto creato",
            "Mario Rossi ha creato l'oggetto “INV-51 — Leggio”.",
            NotificationSeverity.SUCCESS,
            "/inventory",
            Set.of(NotificationAudience.role(RoleEnum.ROLE_ADMIN), NotificationAudience.role(RoleEnum.ROLE_SUPER_ADMIN))
        );
    }

    @Test
    void composesPersonalInventoryRevisionWithDeterministicKey() throws Throwable {
        InventoryItem item = new InventoryItem();
        item.setInventoryNumber("INV-51");
        item.setName("Leggio");
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setId(61L);
        assignment.setItem(item);
        assignment.setUserKeycloakId("recipient-1");
        InventoryAssignmentRevision revision = new InventoryAssignmentRevision();
        revision.setAssignment(assignment);
        revision.setRevisionNumber(3);
        revision.setCreatedBy("system-actor");

        invoke("onInventoryRevisionSaved", joinPoint("save", revision, revision));

        NotificationCommand command = capturePublisherCommand();
        assertThat(command.eventKey()).isEqualTo("inventory:assignment:61:revision:3:user");
        assertThat(command.source()).isEqualTo(NotificationSource.INVENTORY);
        assertThat(command.aggregateType()).isEqualTo("ASSIGNMENT");
        assertThat(command.aggregateId()).isEqualTo("61");
        assertThat(command.operation()).isEqualTo("ASSIGNMENT_REVISION_CREATED");
        assertThat(command.title()).isEqualTo("Inventario: presa visione richiesta");
        assertThat(command.targetPath()).isEqualTo("/inventory");
        assertThat(command.actorId()).isEqualTo("system-actor");
        assertThat(command.actorDisplayName()).isEqualTo("Sistema");
        assertThat(command.audiences()).containsExactly(new NotificationAudience(NotificationAudienceType.USER, "recipient-1"));
    }

    @Test
    void composesFinanceMovementAndUsesRequestKeyForIdempotency() throws Throwable {
        UUID requestKey = UUID.fromString("12345678-1234-1234-1234-1234567890ab");
        MovementRequest request = new MovementRequest(
            1L,
            null,
            null,
            FinancialDirection.EXPENSE,
            LocalDate.of(2026, 9, 3),
            null,
            new BigDecimal("25.00"),
            "Acquisto spartiti",
            null,
            null,
            null,
            requestKey
        );
        MovementDTO movement = new MovementDTO(
            71L,
            2026,
            1L,
            "Cassa",
            null,
            null,
            null,
            null,
            FinancialDirection.EXPENSE,
            FinancialMovementNature.ORDINARY,
            LocalDate.of(2026, 9, 3),
            null,
            new BigDecimal("25.00"),
            "EUR",
            "Acquisto spartiti",
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            1
        );

        aspect.onFinanceOperation(joinPoint("createMovement", movement, request, authentication));

        NotificationCommand command = capturePublisherCommand();
        assertCommand(
            command,
            NotificationSource.FINANCE,
            "MOVEMENT",
            "71",
            "MOVEMENT_CREATED",
            "Economia: movimento registrato",
            "Mario Rossi ha registrato un’uscita di 25,00 € sul conto “Cassa” in data 03/09/2026.",
            NotificationSeverity.INFO,
            "/finance?tab=movements&movementId=71",
            Set.of(
                NotificationAudience.role(RoleEnum.ROLE_ADMIN),
                NotificationAudience.role(RoleEnum.ROLE_SUPER_ADMIN),
                NotificationAudience.role(RoleEnum.ROLE_TREASURER)
            )
        );
        assertThat(command.eventKey()).isEqualTo("finance:movement:71:created:" + requestKey);
    }

    private Object invoke(String method, ProceedingJoinPoint joinPoint) {
        return ReflectionTestUtils.invokeMethod(aspect, method, joinPoint);
    }

    private NotificationCommand capturePublisherCommand() {
        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(publisher).enqueue(captor.capture());
        return captor.getValue();
    }

    private static void assertCommand(
        NotificationCommand command,
        NotificationSource source,
        String aggregateType,
        String aggregateId,
        String operation,
        String title,
        String message,
        NotificationSeverity severity,
        String targetPath,
        Set<NotificationAudience> audiences
    ) {
        assertThat(command.source()).isEqualTo(source);
        assertThat(command.aggregateType()).isEqualTo(aggregateType);
        assertThat(command.aggregateId()).isEqualTo(aggregateId);
        assertThat(command.operation()).isEqualTo(operation);
        assertThat(command.title()).isEqualTo(title);
        assertThat(command.message()).isEqualTo(message);
        assertThat(command.severity()).isEqualTo(severity);
        assertThat(command.targetPath()).isEqualTo(targetPath);
        assertThat(command.actorId()).isEqualTo("actor-1");
        assertThat(command.actorDisplayName()).isEqualTo("Mario Rossi");
        assertThat(command.audiences()).containsExactlyInAnyOrderElementsOf(audiences);
        assertThat(command.targetTenantCode()).isNull();
    }

    private static AlbumsDTO album(Long id, String name, StateEnum state) {
        AlbumsDTO album = new AlbumsDTO();
        album.setId(id);
        album.setName(name);
        album.setState(state);
        return album;
    }

    private static ProceedingJoinPoint joinPoint(String method, Object result, Object... arguments) throws Throwable {
        return targetJoinPoint(method, result, null, arguments);
    }

    private static ProceedingJoinPoint targetJoinPoint(String method, Object result, Object target, Object... arguments) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        lenient().when(signature.getName()).thenReturn(method);
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(joinPoint.getArgs()).thenReturn(arguments);
        lenient().when(joinPoint.getTarget()).thenReturn(target);
        lenient().when(joinPoint.proceed()).thenReturn(result);
        return joinPoint;
    }

    private static JwtAuthenticationToken authentication() {
        Jwt jwt = new Jwt(
            "token",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Map.of("alg", "none"),
            Map.of("sub", "actor-1", "given_name", "Mario", "family_name", "Rossi", "preferred_username", "mrossi")
        );
        return new JwtAuthenticationToken(jwt);
    }
}
