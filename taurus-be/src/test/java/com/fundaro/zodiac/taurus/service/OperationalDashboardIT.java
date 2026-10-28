package com.fundaro.zodiac.taurus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.UserIdentity;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.domain.finance.FinancialAccountType;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentDecision;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRevisionRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.dashboard.DashboardOperationProvider;
import com.fundaro.zodiac.taurus.service.dashboard.DashboardRequestContext;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardDomain;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardOperationType;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardResultStatus;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardSeverity;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalDashboardDTO;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.TransferRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryItemDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryItemRequest;
import com.fundaro.zodiac.taurus.service.impl.FinanceService;
import com.fundaro.zodiac.taurus.service.impl.InventoryService;
import com.fundaro.zodiac.taurus.test.DockerRequirement;
import com.fundaro.zodiac.taurus.test.RecordingStatementInspector;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * La dashboard operativa su PostgreSQL vero: le proiezioni di ogni profilo sui dati di dominio, con utenti inattivi,
 * eventi esclusi, restituzioni parziali ed esercizi diversi; l'isolamento fra due tenant con dati omonimi e il cambio
 * di tenant con lo stesso utente; il risultato parziale quando una tabella di un dominio non risponde; gli obiettivi di
 * prestazione su un tenant con il volume di qualche anno di attività di una banda.
 */
@IntegrationTest
@TestPropertySource(
    properties = {
        "application.base-path=D:/data",
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=8",
        "spring.datasource.hikari.minimum-idle=2",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
        "application.dashboard.enabled=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.fundaro.zodiac.taurus.test.RecordingStatementInspector",
    }
)
// Ogni test prepara uno o due schemi tenant con Liquibase: il limite di progetto di 15 secondi non basta.
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class OperationalDashboardIT {

    private static final Logger LOG = LoggerFactory.getLogger(OperationalDashboardIT.class);

    @MockBean
    ClientRegistrationRepository clientRegistrationRepository;
    @MockBean
    JwtDecoder jwtDecoder;
    @MockBean
    TenantFeatureService tenantFeatureService;

    @Autowired
    TenantSchemaProvisioningService provisioningService;
    @Autowired
    TenantTransactionExecutor transactionExecutor;
    @Autowired
    TenantsRepository tenantsRegistry;
    @Autowired
    UsersRepository usersRepository;
    @Autowired
    OperationalDashboardService dashboardService;
    @Autowired
    List<DashboardOperationProvider> providers;
    @Autowired
    TenantTimeZoneService timeZoneService;
    @Autowired
    FinanceService financeService;
    @Autowired
    InventoryService inventoryService;
    @Autowired
    InventoryItemRepository itemRepository;
    @Autowired
    InventoryAssignmentRepository assignmentRepository;
    @Autowired
    InventoryAssignmentRevisionRepository revisionRepository;
    @Autowired
    NotificationOutboxRepository outboxRepository;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    DataSource dataSource;
    @PersistenceContext
    EntityManager entityManager;

    private final String run = UUID.randomUUID().toString().substring(0, 8);
    private final List<String> tenants = new ArrayList<>();
    private final Set<String> identities = new LinkedHashSet<>();

    @BeforeAll
    static void requireDocker() {
        DockerRequirement.required();
    }

    @AfterEach
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void dropTenantsAndIdentities() {
        try {
            for (String tenant : tenants) provisioningService.dropSchema(tenant);
        } finally {
            for (String tenant : tenants) tenantsRegistry.findByCodeAndDeletedFalse(tenant).ifPresent(tenantsRegistry::delete);
            // Le identità vivono nello schema public, condiviso: si tolgono dopo gli schemi che le referenziano.
            for (String identity : identities) jdbcTemplate.update("DELETE FROM public.user_identity WHERE keycloak_id = ?", identity);
        }
    }

    @Test
    void projectsTheAuthoritativeStateOfEveryProfile() {
        enableFeatures(TenantFeature.FINANCE, TenantFeature.INVENTORY);
        String tenant = tenant("dashboard-profiles");
        Seed seed = seedOrdinaryTenant(tenant);

        // Socio che ha già risposto al primo evento: gli resta il concerto pubblico.
        assertThat(item(tenant, seed.firstMember(), DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED, AuthoritiesConstants.USER).count()).isEqualTo(1);
        // Socio che non ha risposto a nulla: prova e concerto, non gli eventi cancellati, esclusi, bozza, passati o oltre l'orizzonte.
        OperationalItemDTO secondMember = item(tenant, seed.secondMember(), DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED, AuthoritiesConstants.USER);
        assertThat(secondMember.count()).isEqualTo(2);
        assertThat(secondMember.severity()).isEqualTo(DashboardSeverity.WARNING);
        // L'esterno vede solo l'evento PUBLIC.
        assertThat(item(tenant, seed.external(), DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED, AuthoritiesConstants.USER_EXTERNAL).count()).isEqualTo(1);

        // Pubblico atteso: due soci attivi per l'evento COMPLETE, più l'esterno per quello PUBLIC; l'inattivo non conta.
        OperationalItemDTO responses = item(tenant, seed.admin(), DashboardOperationType.CALENDAR_RESPONSES_MISSING, AuthoritiesConstants.ADMIN);
        assertThat(responses.count()).isEqualTo(2);
        assertThat(responses.relatedCount()).isEqualTo(4L);
        assertThat(responses.description()).contains("Prova generale").doesNotContain("Rossi", "Bianchi", "Verdi");

        // Scadenze con restituzioni parziali: la restituzione completa, quella oltre l'orizzonte e quella cancellata non contano.
        OperationalItemDTO expiring = item(tenant, seed.admin(), DashboardOperationType.INVENTORY_ASSIGNMENTS_EXPIRING, AuthoritiesConstants.ADMIN);
        assertThat(expiring.count()).isEqualTo(2);
        assertThat(expiring.severity()).isEqualTo(DashboardSeverity.DANGER);
        assertThat(expiring.targetPath()).isEqualTo("/inventory?attention=expiring");
        OperationalItemDTO ownExpiring = item(tenant, seed.firstMember(), DashboardOperationType.INVENTORY_ASSIGNMENTS_EXPIRING, AuthoritiesConstants.USER);
        assertThat(ownExpiring.count()).isEqualTo(2);
        assertThat(ownExpiring.targetPath()).isEqualTo("/inventory?view=mine&attention=expiring");
        // Le assegnazioni altrui non entrano nella vista personale.
        assertThat(types(tenant, seed.secondMember(), AuthoritiesConstants.USER)).doesNotContain(DashboardOperationType.INVENTORY_ASSIGNMENTS_EXPIRING);
        assertThat(item(tenant, seed.secondMember(), DashboardOperationType.INVENTORY_DECISION_REQUIRED, AuthoritiesConstants.USER).count()).isEqualTo(2);

        // Economia: movimento ordinario dell'anno; trasferimento escluso; anno precedente aperto incluso, poi riportato.
        assertThat(item(tenant, seed.treasurer(), DashboardOperationType.FINANCE_MOVEMENTS_UNRECONCILED, AuthoritiesConstants.TREASURER).count()).isEqualTo(2);
        transactionExecutor.execute(tenant, () -> financeService.rollover(LocalDate.now().getYear() - 2, token(tenant, seed.admin())));
        assertThat(item(tenant, seed.treasurer(), DashboardOperationType.FINANCE_MOVEMENTS_UNRECONCILED, AuthoritiesConstants.TREASURER).count()).isEqualTo(1);

        // Consegne fallite: solo agli amministratori; il Tesoriere non riceve inventario amministrativo o notifiche tecniche.
        OperationalItemDTO failures = item(tenant, seed.admin(), DashboardOperationType.NOTIFICATION_DELIVERY_FAILED, AuthoritiesConstants.ADMIN);
        assertThat(failures.count()).isEqualTo(1);
        assertThat(types(tenant, seed.treasurer(), AuthoritiesConstants.TREASURER)).doesNotContain(
            DashboardOperationType.NOTIFICATION_DELIVERY_FAILED,
            DashboardOperationType.INVENTORY_DECISIONS_PENDING,
            DashboardOperationType.INVENTORY_RETURNS_PENDING,
            DashboardOperationType.CALENDAR_RESPONSES_MISSING,
            DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED
        );
        assertThat(types(tenant, seed.external(), AuthoritiesConstants.USER_EXTERNAL)).doesNotContain(
            DashboardOperationType.CALENDAR_RESPONSES_MISSING,
            DashboardOperationType.NOTIFICATION_DELIVERY_FAILED,
            DashboardOperationType.FINANCE_MOVEMENTS_UNRECONCILED,
            DashboardOperationType.INVENTORY_DECISIONS_PENDING
        );

        // La risoluzione nel dominio autorevole toglie la voce al refresh successivo.
        respond(tenant, seed.events().get("concert"), seed.userIds().get(seed.firstMember()));
        assertThat(types(tenant, seed.firstMember(), AuthoritiesConstants.USER)).doesNotContain(DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED);
    }

    @Test
    void keepsTwoTenantsWithHomonymousDataApartAndNeverReusesAPreviousResult() {
        enableFeatures(TenantFeature.FINANCE, TenantFeature.INVENTORY);
        String first = tenant("dashboard-first");
        String second = tenant("dashboard-second");
        Seed seeded = seedOrdinaryTenant(first);
        // Stesso utente e stessi nomi degli eventi nel secondo tenant, ma qui ha già risposto a tutto.
        String member = seeded.secondMember();
        Long memberId = createUser(second, member, "Luisa", "Bianchi", true, RoleEnum.ROLE_USER);
        Long rehearsal = createEvent(second, "Prova generale", "COMPLETE", 2 * 24, false, false);
        Long concert = createEvent(second, "Concerto in piazza", "PUBLIC", 5 * 24, false, false);
        respond(second, rehearsal, memberId);
        respond(second, concert, memberId);

        assertThat(item(first, member, DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED, AuthoritiesConstants.USER).count()).isEqualTo(2);
        assertThat(types(second, member, AuthoritiesConstants.USER)).doesNotContain(DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED);
        // Di nuovo nel primo: la risposta è ricalcolata, non riusata dal tenant precedente.
        assertThat(item(first, member, DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED, AuthoritiesConstants.USER).count()).isEqualTo(2);

        // L'amministratore del secondo tenant non vede la consegna fallita né le assegnazioni del primo.
        String secondAdmin = "dash-" + run + "-second-admin";
        createUser(second, secondAdmin, "Anna", "Neri", true, RoleEnum.ROLE_ADMIN);
        assertThat(types(second, secondAdmin, AuthoritiesConstants.ADMIN)).doesNotContain(
            DashboardOperationType.NOTIFICATION_DELIVERY_FAILED,
            DashboardOperationType.INVENTORY_ASSIGNMENTS_EXPIRING,
            DashboardOperationType.INVENTORY_DECISIONS_PENDING,
            DashboardOperationType.FINANCE_MOVEMENTS_UNRECONCILED
        );

        // Un token del primo tenant nel contesto del secondo non ottiene nulla: è un errore, non un risultato parziale.
        assertThatThrownBy(() -> TenantContext.call(second, () -> dashboardService.getOperations(token(first, seeded.admin(), AuthoritiesConstants.ADMIN))))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("auth.tenant.mismatch");
    }

    @Test
    void aDomainThatCannotBeReadProducesAPartialResultWithoutLosingTheOthers() {
        enableFeatures(TenantFeature.FINANCE, TenantFeature.INVENTORY);
        String tenant = tenant("dashboard-partial");
        Seed seed = seedOrdinaryTenant(tenant);
        transactionExecutor.execute(tenant, () -> entityManager.createNativeQuery("ALTER TABLE financial_movement RENAME TO financial_movement_offline").executeUpdate());
        try {
            OperationalDashboardDTO result = operations(tenant, seed.admin(), AuthoritiesConstants.ADMIN);

            assertThat(result.status()).isEqualTo(DashboardResultStatus.PARTIAL);
            assertThat(result.unavailableDomains()).containsExactly(DashboardDomain.FINANCE);
            assertThat(result.items()).extracting(OperationalItemDTO::type)
                .contains(DashboardOperationType.CALENDAR_RESPONSES_MISSING, DashboardOperationType.INVENTORY_ASSIGNMENTS_EXPIRING)
                .doesNotContain(DashboardOperationType.FINANCE_MOVEMENTS_UNRECONCILED);
            assertThat(json(result)).doesNotContain("financial_movement", "SQL", "Exception", "relation");
        } finally {
            transactionExecutor.execute(tenant, () -> entityManager.createNativeQuery("ALTER TABLE financial_movement_offline RENAME TO financial_movement").executeUpdate());
        }
    }

    /**
     * Gli obiettivi della specifica su un tenant con il volume di qualche anno di attività: 150 persone, 700 eventi in
     * quattro anni con 73.500 risposte, 1.500 movimenti in tre esercizi, 2.000 assegnazioni con revisioni e prese
     * visione, 5.000 eventi di notifica. I piani PostgreSQL delle query eseguite restano in
     * {@code target/dashboard-query-plans.txt} per la revisione degli indici.
     */
    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void meetsThePerformanceTargetsOnARealisticDataset() throws IOException {
        enableFeatures(TenantFeature.FINANCE, TenantFeature.INVENTORY);
        String tenant = tenant("dashboard-load");
        String admin = seedRealisticTenant(tenant);
        JwtAuthenticationToken adminToken = token(tenant, admin, AuthoritiesConstants.ADMIN);

        for (int warmUp = 0; warmUp < 5; warmUp++) TenantContext.call(tenant, () -> dashboardService.getOperations(adminToken));

        List<Long> durations = new ArrayList<>();
        OperationalDashboardDTO last = null;
        for (int request = 0; request < 40; request++) {
            long startedAt = System.nanoTime();
            last = TenantContext.call(tenant, () -> dashboardService.getOperations(adminToken));
            durations.add((System.nanoTime() - startedAt) / 1_000_000);
        }
        long p95 = percentile(durations, 95);

        RecordingStatementInspector.start();
        OperationalDashboardDTO recorded = TenantContext.call(tenant, () -> dashboardService.getOperations(adminToken));
        List<String> statements = RecordingStatementInspector.stop();

        Map<DashboardDomain, Long> providerP95 = new java.util.EnumMap<>(DashboardDomain.class);
        for (DashboardOperationProvider provider : providers) {
            List<Long> providerDurations = new ArrayList<>();
            for (int request = 0; request < 20; request++) {
                long startedAt = System.nanoTime();
                TenantContext.call(tenant, () -> provider.getOperations(context(tenant, adminToken)));
                providerDurations.add((System.nanoTime() - startedAt) / 1_000_000);
            }
            providerP95.put(provider.domain(), percentile(providerDurations, 95));
        }
        int size = json(last).getBytes(StandardCharsets.UTF_8).length;
        writePlans(tenant, statements, p95, providerP95, size);
        LOG.info("dashboard_load p95Ms={} providerP95Ms={} statements={} responseBytes={} groups={}", p95, providerP95, statements.size(), size, recorded.summary().groupCount());

        assertThat(recorded.status()).isEqualTo(DashboardResultStatus.COMPLETE);
        assertThat(recorded.items()).extracting(OperationalItemDTO::type).contains(
            DashboardOperationType.CALENDAR_RESPONSES_MISSING,
            DashboardOperationType.INVENTORY_ASSIGNMENTS_EXPIRING,
            DashboardOperationType.FINANCE_MOVEMENTS_UNRECONCILED,
            DashboardOperationType.NOTIFICATION_DELIVERY_FAILED
        );
        assertThat(p95).as("p95 della richiesta completa in millisecondi").isLessThan(500);
        assertThat(providerP95).allSatisfy((domain, value) -> assertThat(value).as("p95 del provider %s in millisecondi", domain).isLessThan(200));
        assertThat(statements).as("query di una richiesta completa").hasSizeLessThanOrEqualTo(12);
        assertThat(size).as("dimensione della risposta in byte").isLessThan(32 * 1024);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tenant ordinario: pochi dati scelti per ogni regola della specifica.

    private record Seed(String admin, String firstMember, String secondMember, String external, String treasurer, Map<String, Long> userIds, Map<String, Long> events) {
    }

    private Seed seedOrdinaryTenant(String tenant) {
        String admin = "dash-" + run + "-admin";
        String firstMember = "dash-" + run + "-member-1";
        String secondMember = "dash-" + run + "-member-2";
        String inactive = "dash-" + run + "-inactive";
        String external = "dash-" + run + "-external";
        String treasurer = "dash-" + run + "-treasurer";
        Map<String, Long> userIds = new java.util.HashMap<>();
        userIds.put(admin, createUser(tenant, admin, "Anna", "Neri", true, RoleEnum.ROLE_ADMIN));
        userIds.put(firstMember, createUser(tenant, firstMember, "Mario", "Rossi", true, RoleEnum.ROLE_USER));
        userIds.put(secondMember, createUser(tenant, secondMember, "Luisa", "Bianchi", true, RoleEnum.ROLE_USER));
        userIds.put(inactive, createUser(tenant, inactive, "Paolo", "Verdi", false, RoleEnum.ROLE_USER));
        userIds.put(external, createUser(tenant, external, "Giulia", "Gialli", true, RoleEnum.ROLE_USER_EXTERNAL));
        userIds.put(treasurer, createUser(tenant, treasurer, "Carla", "Blu", true, RoleEnum.ROLE_TREASURER));

        Map<String, Long> events = new java.util.HashMap<>();
        events.put("rehearsal", createEvent(tenant, "Prova generale", "COMPLETE", 2 * 24, false, false));
        events.put("concert", createEvent(tenant, "Concerto in piazza", "PUBLIC", 5 * 24, false, false));
        createEvent(tenant, "Festa patronale", "COMPLETE", 20 * 24, false, false);
        createEvent(tenant, "Prova annullata", "COMPLETE", 3 * 24, true, false);
        createEvent(tenant, "Occorrenza esclusa", "COMPLETE", 4 * 24, false, true);
        createEvent(tenant, "Bozza", "DRAFT", 3 * 24, false, false);
        createEvent(tenant, "Prova di ieri", "COMPLETE", -24, false, false);
        respond(tenant, events.get("rehearsal"), userIds.get(firstMember));

        JwtAuthenticationToken adminToken = token(tenant, admin);
        transactionExecutor.execute(tenant, () -> {
            AccountDTO cash = financeService.createAccount(new AccountRequest("Cassa", null, FinancialAccountType.CASH, "EUR", null, null, 0, null, null, null), adminToken);
            AccountDTO bank = financeService.createAccount(new AccountRequest("Banca", null, FinancialAccountType.CASH, "EUR", null, null, 0, null, null, null), adminToken);
            financeService.createMovement(movement(cash.id(), LocalDate.now().withDayOfYear(1)), adminToken);
            financeService.createMovement(movement(cash.id(), LocalDate.of(LocalDate.now().getYear() - 2, 3, 1)), adminToken);
            financeService.createTransfer(new TransferRequest(cash.id(), bank.id(), LocalDate.now().withDayOfYear(1), null, new BigDecimal("5.00"), "Versamento", null, null), adminToken);
        });

        transactionExecutor.execute(tenant, () -> {
            InventoryItemDTO item = inventoryService.createItem(
                new InventoryItemRequest("INV-1", "Clarinetto", null, 20, null, null, InventoryCondition.GOOD, null, null, null, null),
                adminToken
            );
            LocalDate today = LocalDate.now();
            assignment(item.id(), userIds.get(firstMember), firstMember, InventoryAssignmentStatus.ACTIVE, 2, 0, today.plusDays(3), false);
            assignment(item.id(), userIds.get(firstMember), firstMember, InventoryAssignmentStatus.PARTIALLY_RETURNED, 3, 1, today.minusDays(1), false);
            assignment(item.id(), userIds.get(secondMember), secondMember, InventoryAssignmentStatus.PARTIALLY_RETURNED, 2, 2, today.plusDays(2), false);
            assignment(item.id(), userIds.get(secondMember), secondMember, InventoryAssignmentStatus.ACTIVE, 1, 0, today.plusDays(60), false);
            assignment(item.id(), userIds.get(secondMember), secondMember, InventoryAssignmentStatus.ACTIVE, 1, 0, today.plusDays(1), true);
        });

        transactionExecutor.execute(tenant, () -> outboxRepository.save(outbox("failed-" + run, NotificationStatus.FAILED)));
        return new Seed(admin, firstMember, secondMember, external, treasurer, userIds, events);
    }

    private void assignment(Long itemId, Long userId, String keycloakId, InventoryAssignmentStatus status, int assigned, int returned, LocalDate expiration, boolean deleted) {
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.initializeAudit("test");
        assignment.setDeleted(deleted);
        assignment.setItem(itemRepository.findById(itemId).orElseThrow());
        assignment.setUserIndex(userId);
        assignment.setUserKeycloakId(keycloakId);
        assignment.setUserName("Nome");
        assignment.setUserLastName("Cognome");
        assignment.setAssignedQuantity(assigned);
        assignment.setReturnedQuantity(returned);
        assignment.setAssignedAt(ZonedDateTime.now().minusDays(30));
        assignment.setExpirationDate(expiration);
        assignment.setStatus(status);
        assignment.setCurrentRevision(0);
        assignmentRepository.save(assignment);
    }

    private static MovementRequest movement(Long accountId, LocalDate date) {
        return new MovementRequest(accountId, null, null, FinancialDirection.INCOME, date, null, new BigDecimal("10.00"), "Quota associativa", "Mario Rossi", null, null, null, null);
    }

    private NotificationOutbox outbox(String eventKey, NotificationStatus status) {
        NotificationOutbox event = new NotificationOutbox();
        event.initializeAudit("dispatcher");
        event.setEventKey(eventKey);
        event.setSource(NotificationSource.INVENTORY);
        event.setAggregateType("InventoryItem");
        event.setOperation("CREATE");
        event.setTitle("Mario Rossi ha ricevuto un clarinetto");
        event.setMessage("Testo riservato della notifica");
        event.setActorId("dispatcher");
        event.setActorDisplayName("Sistema");
        event.setOccurredAt(ZonedDateTime.now().minusHours(3));
        event.setStatus(status);
        event.setAttempts(status == NotificationStatus.FAILED ? 8 : 1);
        event.setNextAttemptAt(ZonedDateTime.now().minusHours(2));
        event.setLastError(status == NotificationStatus.FAILED ? "java.lang.IllegalStateException: smtp.example.test refused" : null);
        return event;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Tenant realistico: righe modello create dai servizi e moltiplicate in SQL, così le colonne restano quelle vere.

    private String seedRealisticTenant(String tenant) {
        String admin = "dash-" + run + "-load-admin";
        List<String> keycloakIds = new ArrayList<>();
        transactionExecutor.execute(tenant, () -> {
            for (int index = 0; index < 150; index++) {
                RoleEnum role = index < 110 ? RoleEnum.ROLE_USER : index < 140 ? RoleEnum.ROLE_USER_EXTERNAL : index < 145 ? RoleEnum.ROLE_ADMIN : RoleEnum.ROLE_TREASURER;
                String keycloakId = index == 140 ? admin : "dash-" + run + "-load-" + index;
                persistUser(keycloakId, "Nome" + index, "Cognome" + index, index % 37 != 0, role);
                keycloakIds.add(keycloakId);
            }
        });

        // Una serie settimanale con qualche occorrenza esclusa, il resto eventi singoli.
        Long series = transactionExecutor.execute(tenant, () -> createSeries("Prove del giovedì"));
        sql(tenant, """
            INSERT INTO calendar_event (name, state, start_date, end_date, series_id, original_start_date, series_sequence, series_exception, series_excluded, deleted, insert_by, insert_date, edit_by, edit_date)
            SELECT 'Evento ' || g,
                   CASE WHEN g % 10 = 0 THEN 'DRAFT' WHEN g % 5 = 0 THEN 'PUBLIC' ELSE 'COMPLETE' END,
                   now() - interval '1095 days' + g * interval '50 hours',
                   now() - interval '1095 days' + g * interval '50 hours' + interval '3 hours',
                   CASE WHEN g % 7 = 0 THEN :series END,
                   CASE WHEN g % 7 = 0 THEN now() - interval '1095 days' + g * interval '50 hours' END,
                   CASE WHEN g % 7 = 0 THEN g END,
                   FALSE, g % 7 = 0 AND g % 3 = 0, g % 50 = 0, 'load', now(), 'load', now()
              FROM generate_series(1, 700) g
            """.replace(":series", String.valueOf(series)));
        sql(tenant, """
            INSERT INTO calendar_event_availability (event_id, user_id, availability, response_date)
            SELECT e.id, u.id, CASE WHEN (e.id + u.id) % 3 = 0 THEN 'UNAVAILABLE' ELSE 'AVAILABLE' END, now()
              FROM calendar_event e CROSS JOIN app_user u
             WHERE (e.id * 7 + u.id) % 10 < 7
            """);

        JwtAuthenticationToken adminToken = token(tenant, admin);
        int year = LocalDate.now().getYear();
        transactionExecutor.execute(tenant, () -> {
            AccountDTO cash = financeService.createAccount(new AccountRequest("Cassa", null, FinancialAccountType.CASH, "EUR", null, null, 0, null, null, null), adminToken);
            for (int offset = 2; offset >= 0; offset--) {
                MovementDTO template = financeService.createMovement(movement(cash.id(), LocalDate.of(year - offset, 1, 15)), adminToken);
                cloneRows(
                    "financial_movement",
                    template.id(),
                    "CROSS JOIN generate_series(1, 500) g",
                    Map.of(
                        "booking_date", "(t.booking_date + (g % 300))",
                        "reconciled", offset == 0 ? "(g % 5 <> 0)" : "(g % 50 <> 0)",
                        "request_key", "NULL",
                        "description", "('Movimento ' || g)"
                    )
                );
            }
            if (year - 2 > 0) financeService.rollover(year - 2, adminToken);
        });

        transactionExecutor.execute(tenant, () -> {
            InventoryItemDTO item = inventoryService.createItem(
                new InventoryItemRequest("INV-LOAD", "Leggio", null, 100_000, null, null, InventoryCondition.GOOD, null, null, null, null),
                adminToken
            );
            Long memberIndex = usersRepository.findByKeycloakIdAndDeletedFalse(keycloakIds.get(0)).orElseThrow().getId();
            InventoryAssignmentDTO template = inventoryService.assign(item.id(), new InventoryAssignmentRequest(memberIndex, 0, 1, null, LocalDate.now().plusDays(5), null), adminToken);
            var revision = revisionRepository.findAll().stream().filter(value -> value.getAssignment().getId().equals(template.id())).findFirst().orElseThrow();
            InventoryAssignmentDecision decision = new InventoryAssignmentDecision();
            decision.initializeAudit("test");
            decision.setRevision(revision);
            decision.setDecision(InventoryDecisionType.ACCEPTED);
            decision.setDecidedAt(ZonedDateTime.now());
            decision.setDecidedBy(keycloakIds.get(0));
            decision.setAuthenticatedHash("0".repeat(64));
            entityManager.persist(decision);
            entityManager.flush();

            cloneRows(
                "inventory_assignment",
                template.id(),
                "CROSS JOIN generate_series(1, 2000) g JOIN (SELECT id, keycloak_id, row_number() OVER (ORDER BY id) - 1 AS n FROM app_user) u ON u.n = g % 150",
                Map.of(
                    "user_index", "u.id",
                    "user_keycloak_id", "u.keycloak_id",
                    "status", "CASE WHEN g % 4 = 0 THEN 'RETURNED' WHEN g % 4 = 1 THEN 'PARTIALLY_RETURNED' ELSE 'ACTIVE' END",
                    "returned_quantity", "CASE WHEN g % 4 = 0 THEN t.assigned_quantity ELSE 0 END",
                    "expiration_date", "CASE WHEN g % 3 = 0 THEN NULL ELSE current_date + (g % 400) - 30 END",
                    "deleted", "(g % 40 = 0)"
                )
            );
            cloneRows(
                "inventory_assignment_revision",
                revision.getId(),
                "JOIN inventory_assignment a ON a.id <> t.assignment_id",
                Map.of("assignment_id", "a.id")
            );
            cloneRows(
                "inventory_assignment_decision",
                decision.getId(),
                "JOIN inventory_assignment_revision r ON r.id <> t.revision_id AND r.assignment_id % 5 <> 0",
                Map.of("revision_id", "r.id")
            );

            NotificationOutbox delivered = outboxRepository.save(outbox("load-template-" + run, NotificationStatus.DELIVERED));
            cloneRows(
                "notification_outbox",
                delivered.getId(),
                "CROSS JOIN generate_series(1, 5000) g",
                Map.of(
                    "event_key", "('load-' || g)",
                    "status", "CASE WHEN g % 250 = 0 THEN 'FAILED' ELSE 'DELIVERED' END",
                    "occurred_at", "(now() - g * interval '1 hour')"
                )
            );
        });
        for (String table : List.of("app_user", "calendar_event", "calendar_event_availability", "financial_movement", "inventory_assignment", "inventory_assignment_revision", "inventory_assignment_decision", "notification_outbox")) {
            sql(tenant, "ANALYZE " + table);
        }
        return admin;
    }

    /**
     * Copia la riga modello {@code t} di una tabella per ogni riga di {@code from}, sostituendo le colonne indicate: le
     * altre colonne, lette dal catalogo, restano quelle scritte dal servizio.
     */
    @SuppressWarnings("unchecked")
    private void cloneRows(String table, Long templateId, String from, Map<String, String> overrides) {
        entityManager.flush();
        List<String> columns = entityManager.createNativeQuery(
            "SELECT column_name FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = :table AND column_name <> 'id' ORDER BY ordinal_position"
        ).setParameter("table", table).getResultList();
        String select = columns.stream().map(column -> overrides.getOrDefault(column, "t." + column)).collect(Collectors.joining(", "));
        entityManager.createNativeQuery(
            "INSERT INTO " + table + " (" + String.join(", ", columns) + ") SELECT " + select + " FROM " + table + " t " + from + " WHERE t.id = " + templateId
        ).executeUpdate();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Piani delle query: EXPLAIN (GENERIC_PLAN) di PostgreSQL 16+, senza valori dei parametri.

    private void writePlans(String tenant, List<String> statements, long p95, Map<DashboardDomain, Long> providerP95, int size) throws IOException {
        StringBuilder report = new StringBuilder()
            .append("Dashboard operativa - dataset realistico\n")
            .append("p95 richiesta completa: ").append(p95).append(" ms\n")
            .append("p95 per provider: ").append(providerP95).append('\n')
            .append("query per richiesta: ").append(statements.size()).append('\n')
            .append("dimensione risposta: ").append(size).append(" byte\n\n");
        String schema = transactionExecutor.execute(tenant, () -> String.valueOf(entityManager.createNativeQuery("SELECT current_schema()").getSingleResult()));
        try (Connection connection = simpleProtocolConnection(); Statement sql = connection.createStatement()) {
            sql.execute("SET search_path TO \"" + schema.replace("\"", "\"\"") + "\"");
            for (String statement : statements) {
                report.append("-- ").append(statement).append('\n');
                report.append(plan(sql, statement)).append('\n');
            }
        } catch (SQLException exception) {
            report.append("piani non disponibili: ").append(exception.getMessage()).append('\n');
        }
        Path target = Path.of("target", "dashboard-query-plans.txt");
        Files.createDirectories(target.getParent());
        Files.writeString(target, report.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Il piano generico richiede il protocollo semplice: con quello esteso, il predefinito di JDBC, PostgreSQL aspetta i
     * valori dei parametri. Una connessione a parte, chiusa dopo i piani, evita di cambiare quelle del pool.
     */
    private Connection simpleProtocolConnection() throws SQLException {
        HikariDataSource pool = dataSource.unwrap(HikariDataSource.class);
        Properties properties = new Properties();
        properties.setProperty("user", pool.getUsername());
        properties.setProperty("password", pool.getPassword());
        properties.setProperty("preferQueryMode", "simple");
        return DriverManager.getConnection(pool.getJdbcUrl(), properties);
    }

    private static String plan(Statement sql, String statement) {
        Matcher matcher = Pattern.compile("\\?\\d*").matcher(statement);
        StringBuilder numbered = new StringBuilder();
        int index = 0;
        while (matcher.find()) matcher.appendReplacement(numbered, "\\$" + ++index);
        matcher.appendTail(numbered);
        List<String> rows = new ArrayList<>();
        try (ResultSet result = sql.executeQuery("EXPLAIN (GENERIC_PLAN) " + numbered)) {
            while (result.next()) rows.add(result.getString(1));
            return String.join("\n", rows);
        } catch (SQLException exception) {
            // Un parametro di cui PostgreSQL non deduce il tipo non ha piano generico: il resto del report resta valido.
            return "piano generico non disponibile: " + String.valueOf(exception.getMessage()).lines().findFirst().orElse("");
        }
    }

    // ---------------------------------------------------------------------------------------------------------------

    private String tenant(String prefix) {
        String code = prefix + "-" + run;
        Date now = new Date();
        Tenants tenant = new Tenants();
        tenant.setName(code);
        tenant.setCode(code);
        tenant.setActive(true);
        tenant.setFinanceEnabled(true);
        tenant.setInventoryEnabled(true);
        tenant.setTimeZone("Europe/Rome");
        tenant.setDeleted(false);
        tenant.setInsertBy("test");
        tenant.setInsertDate(now);
        tenant.setEditBy("test");
        tenant.setEditDate(now);
        tenant.setEntityVersion(0L);
        tenantsRegistry.save(tenant);
        tenants.add(code);
        provisioningService.provision(code);
        return code;
    }

    private void enableFeatures(TenantFeature... features) {
        Set<TenantFeature> enabled = Set.of(features);
        when(tenantFeatureService.isEnabled(any(TenantFeature.class))).thenAnswer(invocation -> enabled.contains(invocation.getArgument(0, TenantFeature.class)));
    }

    private Long createUser(String tenant, String keycloakId, String name, String lastName, boolean active, RoleEnum role) {
        return transactionExecutor.execute(tenant, () -> persistUser(keycloakId, name, lastName, active, role));
    }

    private Long persistUser(String keycloakId, String name, String lastName, boolean active, RoleEnum role) {
        UserIdentity identity = entityManager
            .createQuery("select identity from UserIdentity identity where identity.keycloakId = :subject", UserIdentity.class)
            .setParameter("subject", keycloakId)
            .getResultStream()
            .findFirst()
            .orElse(null);
        if (identity == null) {
            identity = new UserIdentity();
            identity.setKeycloakId(keycloakId);
            identity.initializeAudit("test");
            entityManager.persist(identity);
            identities.add(keycloakId);
        }
        Users user = new Users();
        user.setKeycloakId(keycloakId);
        user.setActive(active);
        user.setName(name);
        user.setLastName(lastName);
        user.setEmail(keycloakId + "@example.test");
        user.setUserIdentity(identity);
        user.setRoles(new LinkedHashSet<>(Set.of(role)));
        user.setDeleted(false);
        user.setInsertBy("test");
        user.setInsertDate(new Date());
        user.setEditBy("test");
        user.setEditDate(new Date());
        return usersRepository.save(user).getId();
    }

    private Long createEvent(String tenant, String name, String state, long hoursFromNow, boolean deleted, boolean seriesExcluded) {
        return transactionExecutor.execute(tenant, () -> {
            // Un'occorrenza esclusa appartiene sempre a una serie: lo impone ck_calendar_event_series_fields.
            Long series = seriesExcluded ? createSeries(name) : null;
            entityManager.createNativeQuery(
                "INSERT INTO calendar_event (name, state, start_date, end_date, series_id, original_start_date, series_sequence, series_exception, series_excluded, deleted, insert_by, insert_date, edit_by, edit_date)"
                    + " VALUES (:name, :state, now() + make_interval(hours => :hours), now() + make_interval(hours => :hours + 2),"
                    + " :series, CASE WHEN :excluded THEN now() + make_interval(hours => :hours) END, CASE WHEN :excluded THEN 1 END,"
                    + " FALSE, :excluded, :deleted, 'test', now(), 'test', now())"
            )
                .setParameter("name", name)
                .setParameter("state", state)
                .setParameter("hours", (int) hoursFromNow)
                .setParameter("series", series)
                .setParameter("excluded", seriesExcluded)
                .setParameter("deleted", deleted)
                .executeUpdate();
            return ((Number) entityManager.createNativeQuery("SELECT MAX(id) FROM calendar_event").getSingleResult()).longValue();
        });
    }

    private Long createSeries(String name) {
        entityManager.createNativeQuery(
            "INSERT INTO calendar_event_series (name, time_zone, first_start_local, duration_minutes, frequency, interval_value, end_type, occurrence_count, insert_by, edit_by)"
                + " VALUES (:name, 'Europe/Rome', localtimestamp, 120, 'WEEKLY', 1, 'COUNT', 52, 'test', 'test')"
        ).setParameter("name", name).executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT MAX(id) FROM calendar_event_series").getSingleResult()).longValue();
    }

    private void respond(String tenant, Long eventId, Long userId) {
        transactionExecutor.execute(tenant, () ->
            entityManager.createNativeQuery("INSERT INTO calendar_event_availability (event_id, user_id, availability, response_date) VALUES (:event, :user, 'AVAILABLE', now())")
                .setParameter("event", eventId)
                .setParameter("user", userId)
                .executeUpdate()
        );
    }

    private void sql(String tenant, String statement) {
        transactionExecutor.execute(tenant, () -> entityManager.createNativeQuery(statement).executeUpdate());
    }

    private OperationalDashboardDTO operations(String tenant, String subject, String... authorities) {
        return TenantContext.call(tenant, () -> dashboardService.getOperations(token(tenant, subject, authorities)));
    }

    private List<DashboardOperationType> types(String tenant, String subject, String... authorities) {
        return operations(tenant, subject, authorities).items().stream().map(OperationalItemDTO::type).toList();
    }

    private OperationalItemDTO item(String tenant, String subject, DashboardOperationType type, String... authorities) {
        return operations(tenant, subject, authorities).items().stream()
            .filter(value -> value.type() == type)
            .findFirst()
            .orElseThrow(() -> new AssertionError(type + " assente per " + subject));
    }

    private DashboardRequestContext context(String tenant, JwtAuthenticationToken token) {
        var zone = TenantContext.call(tenant, timeZoneService::currentZoneId);
        Set<String> authorities = token.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return new DashboardRequestContext(token.getName(), authorities, ZonedDateTime.now(zone), zone, token);
    }

    private String json(OperationalDashboardDTO value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static long percentile(List<Long> values, int percentile) {
        List<Long> sorted = values.stream().sorted().toList();
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }

    private static JwtAuthenticationToken token(String tenant, String subject, String... authorities) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).claim("tenant", tenant)
            .claim("given_name", "Anna").claim("family_name", "Neri")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt, Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }
}
