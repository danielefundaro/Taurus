package com.fundaro.zodiac.taurus.service.calendarfeed;

import static com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.*;
import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.multitenancy.*;
import com.fundaro.zodiac.taurus.repository.*;
import com.fundaro.zodiac.taurus.repository.calendarfeed.*;
import com.fundaro.zodiac.taurus.service.impl.DataErasureService;
import com.fundaro.zodiac.taurus.test.DockerRequirement;
import com.fundaro.zodiac.taurus.test.RecordingStatementInspector;
import com.fundaro.zodiac.taurus.web.rest.CalendarSubscriptionResource;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

/**
 * The feed from end to end on a real PostgreSQL: the lifecycle of the credential, what the ICS
 * answer contains, the limits of the public endpoint and the automatic revocations.
 *
 * <p>The anonymous download is exercised through {@link CalendarSubscriptionResource}, so the
 * conditional request, the headers and the status codes are the ones a client receives; everything
 * else goes through the services, inside the tenant context the application opens for them.
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
        "application.base-path=D:/data",
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=8",
        "spring.datasource.hikari.minimum-idle=2",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
        "application.calendar-feed.public-base-url=https://taurus.example",
        "application.calendar-feed.rate-limit-per-token-hour=5",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.fundaro.zodiac.taurus.test.RecordingStatementInspector",
    }
)
// Ogni test prepara due schemi tenant con Liquibase: il limite di progetto di 15 secondi non basta.
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CalendarFeedIT {

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private CalendarFeedManagementService feeds;

    @Autowired
    private CalendarSubscriptionResource subscriptions;

    @Autowired
    private CalendarFeedOwnerRevocationService ownerRevocation;

    @Autowired
    private CalendarFeedTenantRevocationService tenantRevocation;

    @Autowired
    private CalendarFeedTombstoneScheduler maintenance;

    @Autowired
    private TenantSchemaProvisioningService provisioning;

    @Autowired
    private TenantTransactionExecutor transactions;

    @Autowired
    private TenantsRepository tenantsRegistry;

    @Autowired
    private UsersRepository users;

    @Autowired
    private UserIdentityRepository identities;

    @Autowired
    private CalendarEventsRepository events;

    @Autowired
    private CalendarFeedSubscriptionRepository feedRows;

    @Autowired
    private CalendarFeedTokenRegistryRepository registry;

    @Autowired
    private CalendarFeedAuditRepository audits;

    @Autowired
    private CalendarFeedIdempotencyRepository receipts;

    @Autowired
    private DataErasureService erasure;

    private String ownerTenant;
    private String otherTenant;
    private Long ownerTenantId;
    private Long otherTenantId;

    @BeforeAll
    static void requireDocker() {
        DockerRequirement.required();
    }

    @BeforeEach
    void provisionBothTenants() {
        ownerTenant = "feed-owner-" + UUID.randomUUID();
        otherTenant = "feed-other-" + UUID.randomUUID();
        ownerTenantId = registerTenant(ownerTenant);
        otherTenantId = registerTenant(otherTenant);
    }

    @AfterEach
    void dropBothTenants() {
        try {
            dropTenant(ownerTenant, ownerTenantId);
        } finally {
            dropTenant(otherTenant, otherTenantId);
        }
    }

    @Test
    void showsTheUrlOnceAndKeepsTheHistoryOfTheCredential() {
        createUser(ownerTenant, "member-1", RoleEnum.ROLE_USER);
        SecretFeed created = create(ownerTenant, "member-1", "Il mio calendario");

        assertThat(created.subscriptionUrl()).startsWith("https://taurus.example/api/calendar-subscriptions/v1/");
        String first = tokenOf(created);
        assertThat(download(first).getStatusCode()).isEqualTo(HttpStatus.OK);

        List<Feed> listed = transactions.execute(ownerTenant, () -> feeds.listPersonal(authentication(ownerTenant, "member-1")));
        assertThat(listed).singleElement().satisfies(feed -> {
            assertThat(feed.tokenFingerprint()).isNotBlank().hasSize(12);
            assertThat(feed.ownerName()).isEqualTo("Socio Uno");
        });
        // No field of the list carries the credential: the fingerprint is not part of the token.
        assertThat(listed.get(0).toString()).doesNotContain(first);

        SecretFeed rotated = transactions.execute(ownerTenant, () ->
            feeds.rotate(created.id(), new RotateRequest(UUID.randomUUID()), false, authentication(ownerTenant, "member-1")));
        assertThat(download(first).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(download(tokenOf(rotated)).getStatusCode()).isEqualTo(HttpStatus.OK);

        transactions.execute(ownerTenant, () -> feeds.revoke(created.id(), false, authentication(ownerTenant, "member-1")));
        assertThat(download(tokenOf(rotated)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        List<AuditEntry> history = transactions.execute(ownerTenant, () ->
            feeds.history(created.id(), false, authentication(ownerTenant, "member-1")));
        assertThat(history).extracting(AuditEntry::action).containsExactly("REVOKED", "ROTATED", "CREATED");
        assertThat(history).allSatisfy(entry -> assertThat(entry.actor()).isEqualTo("member-1"));
        assertThat(history).extracting(AuditEntry::tokenVersion).containsExactly(2, 2, 1);
    }

    @Test
    void servesOnlyTheEventsTheScopeAllowsAndNeverADraft() {
        createUser(ownerTenant, "member-1", RoleEnum.ROLE_USER);
        createUser(ownerTenant, "guest-1", RoleEnum.ROLE_USER_EXTERNAL);
        Instant start = Instant.now().plus(7, ChronoUnit.DAYS);
        createEvent(ownerTenant, "Concerto pubblico", StateEnum.PUBLIC, start);
        createEvent(ownerTenant, "Prova interna", StateEnum.COMPLETE, start.plus(1, ChronoUnit.DAYS));
        createEvent(ownerTenant, "Bozza riservata", StateEnum.DRAFT, start.plus(2, ChronoUnit.DAYS));
        // Lo stesso nome nell'altro tenant: il feed non deve attraversare lo schema.
        createEvent(otherTenant, "Evento di un altro tenant", StateEnum.PUBLIC, start);

        String internal = tokenOf(create(ownerTenant, "member-1", "Interno"));
        String external = tokenOf(create(ownerTenant, "guest-1", "Pubblico"));

        String internalBody = body(download(internal));
        assertThat(internalBody).contains("Concerto pubblico", "Prova interna");
        assertThat(internalBody).doesNotContain("Bozza riservata", "Evento di un altro tenant");

        String externalBody = body(download(external));
        assertThat(externalBody).contains("Concerto pubblico");
        assertThat(externalBody).doesNotContain("Prova interna", "Bozza riservata");
        // Il link del livello standard viaggia sull'UID: nessun id numerico esce dallo schema.
        assertThat(externalBody).doesNotContain("/calendar/1", "/calendar/2");
    }

    @Test
    void answersTheConditionalRequestTheHeadRequestAndTheEmptyCalendar() {
        createUser(ownerTenant, "member-1", RoleEnum.ROLE_USER);
        String token = tokenOf(create(ownerTenant, "member-1", "Vuoto"));

        ResponseEntity<byte[]> first = download(token);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE)).isEqualTo("text/calendar;charset=utf-8");
        assertThat(first.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).isEqualTo("inline; filename=\"calendar.ics\"");
        assertThat(first.getHeaders().getCacheControl()).isEqualTo("private, max-age=300, must-revalidate");
        assertThat(first.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        // Un calendario senza eventi resta un calendario valido.
        assertThat(body(first)).contains("BEGIN:VCALENDAR", "END:VCALENDAR").doesNotContain("BEGIN:VEVENT");

        String etag = first.getHeaders().getETag();
        ResponseEntity<byte[]> conditional = request(token, "GET", etag);
        assertThat(conditional.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(conditional.getBody()).isNull();
        assertThat(conditional.getHeaders().getETag()).isEqualTo(etag);

        ResponseEntity<byte[]> head = request(token, "HEAD", null);
        assertThat(head.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(head.getBody()).isNull();
        assertThat(head.getHeaders().getETag()).isEqualTo(etag);

        assertThat(download("non-esiste").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void stopsWhenTheOwnerLosesTheRoleAndRecordsWhy() {
        Long userId = createUser(ownerTenant, "member-1", RoleEnum.ROLE_USER);
        SecretFeed feed = create(ownerTenant, "member-1", "Personale");
        String token = tokenOf(feed);
        assertThat(download(token).getStatusCode()).isEqualTo(HttpStatus.OK);

        transactions.execute(ownerTenant, () -> {
            Users owner = users.findById(userId).orElseThrow();
            owner.getRoles().clear();
            owner.getRoles().add(RoleEnum.ROLE_ARCHIVIST);
            users.save(owner);
            ownerRevocation.revokeUnauthorized(owner, "admin-1");
        });

        assertThat(download(token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        transactions.execute(ownerTenant, () -> {
            assertThat(feedRows.findById(feed.id()).orElseThrow().getStatus()).isEqualTo(CalendarFeedStatus.REVOKED);
            assertThat(audits.findAllBySubscriptionIdOrderByOccurredAtDescIdDesc(feed.id()))
                .first().satisfies(entry -> {
                    assertThat(entry.getAction()).isEqualTo(CalendarFeedAuditAction.REVOKED);
                    assertThat(entry.getSource()).isEqualTo(CalendarFeedAuditSource.OWNER_CHANGED);
                    assertThat(entry.getReason()).isEqualTo("owner_not_authorized");
                });
        });
        assertThat(activeGlobalRows(ownerTenantId)).isZero();
    }

    @Test
    void revokesEveryFeedWhenTheTenantIsDeactivated() {
        createUser(ownerTenant, "member-1", RoleEnum.ROLE_USER);
        SecretFeed feed = create(ownerTenant, "member-1", "Personale");
        String token = tokenOf(feed);

        tenantRevocation.revokeAll(ownerTenantId, ownerTenant, "super-admin", "tenant_deactivated");

        assertThat(download(token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(activeGlobalRows(ownerTenantId)).isZero();
        transactions.execute(ownerTenant, () ->
            assertThat(feedRows.findById(feed.id()).orElseThrow().getStatus()).isEqualTo(CalendarFeedStatus.REVOKED));
    }

    @Test
    void leavesNoGlobalCredentialWhenTheTenantIsErased() {
        createUser(ownerTenant, "member-1", RoleEnum.ROLE_USER);
        create(ownerTenant, "member-1", "Personale");
        assertThat(activeGlobalRows(ownerTenantId)).isEqualTo(1);

        erasure.eraseTenantData(ownerTenant);

        assertThat(registry.findAllByStatus(CalendarFeedStatus.ACTIVE))
            .noneMatch(row -> ownerTenantId.equals(row.getTenantId()));
        assertThat(registry.findAll()).noneMatch(row -> ownerTenantId.equals(row.getTenantId()));
    }

    @Test
    void refusesTheRequestsBeyondTheHourlyLimitOfTheToken() {
        createUser(ownerTenant, "member-1", RoleEnum.ROLE_USER);
        String token = tokenOf(create(ownerTenant, "member-1", "Personale"));

        List<HttpStatus> outcomes = new ArrayList<>();
        for (int attempt = 0; attempt < 6; attempt++) outcomes.add((HttpStatus) download(token).getStatusCode());

        assertThat(outcomes.subList(0, 5)).containsOnly(HttpStatus.OK);
        assertThat(outcomes.get(5)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void writesTheLastAccessAtMostOncePerDayAndReadsOnlyWhatTheFeedPublishes() {
        createUser(ownerTenant, "member-1", RoleEnum.ROLE_USER);
        createEvent(ownerTenant, "Concerto pubblico", StateEnum.PUBLIC, Instant.now().plus(3, ChronoUnit.DAYS));
        String token = tokenOf(create(ownerTenant, "member-1", "Personale"));

        RecordingStatementInspector.start();
        assertThat(download(token).getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> statements = RecordingStatementInspector.stop();

        Instant afterFirst = transactions.execute(ownerTenant, () -> feedRows.findAll().get(0).getLastAccessedAt());
        assertThat(afterFirst).isNotNull();
        download(token);
        assertThat(transactions.execute(ownerTenant, () -> feedRows.findAll().get(0).getLastAccessedAt())).isEqualTo(afterFirst);

        String executed = String.join(" ", statements).toLowerCase(Locale.ROOT);
        assertThat(executed).doesNotContain("calendar_event_availability", "calendar_event_presence", "event_cost");
        assertThat(statements).as("query di un download").hasSizeLessThanOrEqualTo(8);
    }

    @Test
    void dropsOldReceiptsAndClosesTheGlobalRowsWithoutASubscription() {
        createUser(ownerTenant, "member-1", RoleEnum.ROLE_USER);
        SecretFeed feed = create(ownerTenant, "member-1", "Personale");
        transactions.execute(ownerTenant, () -> {
            CalendarFeedIdempotency receipt = receipts.findAll().get(0);
            receipt.setCreatedAt(Instant.now().minus(60, ChronoUnit.DAYS));
            receipts.save(receipt);
        });
        // Una riga globale che nessuna sottoscrizione rivendica più: una transazione interrotta,
        // o un difetto, lascerebbe una credenziale valida e invisibile nell'interfaccia.
        UUID orphan = UUID.randomUUID();
        registry.save(globalRow(orphan, ownerTenantId));

        maintenance.cleanup();

        assertThat(transactions.execute(ownerTenant, () -> receipts.count())).isZero();
        assertThat(registry.findAllByStatus(CalendarFeedStatus.ACTIVE))
            .extracting(CalendarFeedTokenRegistry::getSubscriptionId)
            .contains(feed.id()).doesNotContain(orphan);
    }

    @Test
    void refusesToReadOrManageAFeedOfAnotherTenant() {
        createUser(ownerTenant, "member-1", RoleEnum.ROLE_USER);
        createUser(otherTenant, "member-2", RoleEnum.ROLE_USER);
        SecretFeed feed = create(ownerTenant, "member-1", "Personale");

        assertThatThrownBy(() -> transactions.execute(otherTenant, () ->
            feeds.history(feed.id(), true, authentication(otherTenant, "member-2"))))
            .isInstanceOf(RequestAlertException.class);
        assertThat(transactions.execute(otherTenant, () -> feeds.listAdmin())).isEmpty();
    }

    // -----------------------------------------------------------------------------------------------------------
    // Helpers

    private SecretFeed create(String tenant, String keycloakId, String name) {
        return transactions.execute(tenant, () -> feeds.createPersonal(
            new CreateRequest(name, null, CalendarFeedDetailLevel.STANDARD, 90, 18, UUID.randomUUID()),
            authentication(tenant, keycloakId)));
    }

    private ResponseEntity<byte[]> download(String token) {
        return request(token, "GET", null);
    }

    private ResponseEntity<byte[]> request(String token, String method, String ifNoneMatch) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/calendar-subscriptions/v1/" + token + "/calendar.ics");
        request.setRemoteAddr("203.0.113." + (Math.abs(token.hashCode()) % 200 + 1));
        return subscriptions.download(token, ifNoneMatch, null, request);
    }

    private static String body(ResponseEntity<byte[]> response) {
        return new String(Objects.requireNonNull(response.getBody()), StandardCharsets.UTF_8);
    }

    private static String tokenOf(SecretFeed feed) {
        String url = feed.subscriptionUrl();
        return url.substring(url.indexOf("/v1/") + 4, url.lastIndexOf('/'));
    }

    private long activeGlobalRows(Long tenantId) {
        return registry.findAllByStatus(CalendarFeedStatus.ACTIVE).stream()
            .filter(row -> tenantId.equals(row.getTenantId())).count();
    }

    private CalendarFeedTokenRegistry globalRow(UUID subscriptionId, Long tenantId) {
        CalendarFeedTokenRegistry row = new CalendarFeedTokenRegistry();
        row.setTokenDigest(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        row.setSubscriptionId(subscriptionId);
        row.setTenantId(tenantId);
        row.setTokenVersion(1);
        row.setStatus(CalendarFeedStatus.ACTIVE);
        row.setCreatedAt(Instant.now());
        return row;
    }

    private Long createUser(String tenant, String keycloakId, RoleEnum role) {
        // user_identity vive nello schema public: è condivisa tra i tenant e va riusata.
        UserIdentity identity = transactions.execute(null, () -> identities.findByKeycloakId(keycloakId).orElseGet(() -> {
            UserIdentity created = new UserIdentity();
            created.setKeycloakId(keycloakId);
            created.initializeAudit(keycloakId);
            return identities.save(created);
        }));
        return transactions.execute(tenant, () -> {
            Users user = new Users();
            user.setUserIdentity(identity);
            user.setKeycloakId(keycloakId);
            user.setName("Socio");
            user.setLastName("Uno");
            user.setActive(true);
            user.setDeleted(false);
            user.getRoles().add(role);
            Date now = new Date();
            user.setInsertBy(keycloakId);
            user.setInsertDate(now);
            user.setEditBy(keycloakId);
            user.setEditDate(now);
            return users.save(user).getId();
        });
    }

    private void createEvent(String tenant, String name, StateEnum state, Instant start) {
        transactions.execute(tenant, () -> {
            CalendarEvents event = new CalendarEvents();
            event.setName(name);
            event.setDescription("Descrizione di " + name);
            event.setLocation("Auditorium");
            event.setState(state);
            event.setDeleted(false);
            event.setSeriesExcluded(false);
            event.setStartDate(Date.from(start));
            event.setEndDate(Date.from(start.plus(2, ChronoUnit.HOURS)));
            Date now = new Date();
            event.setInsertBy("admin-1");
            event.setInsertDate(now);
            event.setEditBy("admin-1");
            event.setEditDate(now);
            events.save(event);
        });
    }

    private Long registerTenant(String code) {
        Date now = new Date();
        Tenants tenant = new Tenants();
        tenant.setName(code);
        tenant.setCode(code);
        tenant.setActive(true);
        tenant.setExternalCalendarFeedEnabled(true);
        tenant.setDeleted(false);
        tenant.setInsertBy("super-admin");
        tenant.setInsertDate(now);
        tenant.setEditBy("super-admin");
        tenant.setEditDate(now);
        tenant.setEntityVersion(0L);
        Long id = tenantsRegistry.save(tenant).getId();
        provisioning.provision(code);
        provisioning.linkTenant(id, code);
        return id;
    }

    private void dropTenant(String code, Long id) {
        try {
            provisioning.dropSchema(code);
        } catch (RuntimeException ignored) {
            // Un test che cancella il tenant ha già lasciato cadere lo schema.
        }
        transactions.execute(null, () -> {
            if (id != null) registry.deleteAllByTenantId(id);
            tenantsRegistry.findByCodeAndDeletedFalse(code).ifPresent(tenantsRegistry::delete);
        });
    }

    private JwtAuthenticationToken authentication(String tenantCode, String subject) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject)
            .claim("tenant", tenantCode)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }
}
