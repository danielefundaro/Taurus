package com.fundaro.zodiac.taurus.service.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.TaurusApp;
import com.fundaro.zodiac.taurus.config.EmbeddedSQL;
import com.fundaro.zodiac.taurus.config.JacksonConfiguration;
import com.fundaro.zodiac.taurus.config.TestSecurityConfiguration;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportIssue;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingImportJob;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingJobStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaNameResolver;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.InstrumentsRepository;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryItemRepository;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportIssueRepository;
import com.fundaro.zodiac.taurus.repository.onboarding.OnboardingImportJobRepository;
import com.fundaro.zodiac.taurus.service.impl.TenantStorageService;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {TaurusApp.class, JacksonConfiguration.class, TestSecurityConfiguration.class})
@EmbeddedSQL
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 2, unit = TimeUnit.MINUTES)
@TestPropertySource(
    properties = {
        "application.base-path=target/test-data/onboarding-import-it",
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
        "application.score-playback.enabled=true",
        // La ricognizione serve alla prova di riavvio, che la chiama a mano: la pianificazione resta ferma.
        "application.onboarding.recovery-enabled=true",
        "application.onboarding.worker-delay=600000",
    }
)
class OnboardingImportIT {

    private static final String ACTOR = "onboarding-admin";
    private static final String VALID_CSV = "riferimento,nome,descrizione\r\nSTR-1,Clarinetto,Legni\r\n";
    private static final String INVALID_CSV = "riferimento,nome,descrizione\r\nSTR-2,,Nome obbligatorio\r\n";

    @MockBean
    ClientRegistrationRepository clientRegistrationRepository;
    @MockBean
    JwtDecoder jwtDecoder;
    @MockBean
    KeycloakService keycloakService;

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    TenantSchemaProvisioningService provisioningService;
    @Autowired
    TenantTransactionExecutor transactionExecutor;
    @Autowired
    TenantStorageService storageService;
    @Autowired
    TenantsRepository tenantsRepository;
    @Autowired
    OnboardingImportJobRepository jobs;
    @Autowired
    OnboardingImportIssueRepository issues;
    @Autowired
    InstrumentsRepository instruments;
    @Autowired
    InventoryItemRepository inventory;
    @Autowired
    OnboardingTemplateService templates;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    OnboardingRecoveryScheduler recovery;
    @Autowired
    TenantSchemaNameResolver schemaNames;

    private final String tenantOne = "onboarding-a-" + UUID.randomUUID();
    private final String tenantTwo = "onboarding-b-" + UUID.randomUUID();
    private final String playbackTenant = "onboarding-c-" + UUID.randomUUID();
    private final String limitsTenant = "onboarding-d-" + UUID.randomUUID();
    private final String loadTenant = "onboarding-e-" + UUID.randomUUID();

    @BeforeAll
    void provisionTenants() {
        createTenant(tenantOne);
        createTenant(tenantTwo);
        createTenant(playbackTenant);
        createTenant(limitsTenant);
        createTenant(loadTenant);
        Tenants withPlayback = tenantsRepository.findByCodeAndDeletedFalse(playbackTenant).orElseThrow();
        withPlayback.setScorePlaybackEnabled(true);
        tenantsRepository.save(withPlayback);
        for (String tenant : new String[] { tenantOne, tenantTwo, playbackTenant, limitsTenant, loadTenant }) {
            provisioningService.provision(tenant);
            provisioningService.linkTenant(tenantsRepository.findByCodeAndDeletedFalse(tenant).orElseThrow().getId(), tenant);
        }
        when(keycloakService.getGroupIdByName(tenantOne)).thenReturn("group-a");
    }

    @AfterAll
    void cleanup() throws Exception {
        for (String tenant : new String[] { tenantOne, tenantTwo, playbackTenant, limitsTenant, loadTenant }) {
            storageService.deleteTenantDirectory(tenant);
            provisioningService.dropSchema(tenant);
        }
        jdbcTemplate.update(
            "DELETE FROM public.tenant WHERE code IN (?, ?, ?, ?, ?)",
            tenantOne,
            tenantTwo,
            playbackTenant,
            limitsTenant,
            loadTenant
        );
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void importsOnceKeepsStagingIsolatedAndRejectsInvalidData() throws Exception {
        mockMvc.perform(get("/api/onboarding/context").with(authentication(tenantOne, "ROLE_USER")))
            .andExpect(status().isForbidden());

        UUID uploadKey = UUID.randomUUID();
        long jobId = upload(tenantOne, uploadKey, VALID_CSV, "valid.csv");
        long duplicateId = upload(tenantOne, uploadKey, VALID_CSV, "retry.csv");
        assertThat(duplicateId).isEqualTo(jobId);
        awaitStatus(tenantOne, jobId, OnboardingJobStatus.READY);

        mockMvc.perform(get("/api/onboarding/imports/{id}/rows", jobId).with(authentication(tenantOne, "ROLE_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.content[0].values.nome").value("Clarinetto"))
            .andExpect(jsonPath("$.content[0].status").value("VALID"));

        mockMvc.perform(get("/api/onboarding/imports/{id}", jobId).with(authentication(tenantTwo, "ROLE_ADMIN")))
            .andExpect(status().isNotFound());

        UUID applyKey = UUID.randomUUID();
        apply(tenantOne, jobId, applyKey);
        apply(tenantOne, jobId, applyKey);
        awaitStatus(tenantOne, jobId, OnboardingJobStatus.COMPLETED);

        transactionExecutor.execute(tenantOne, () -> {
            assertThat(jobs.count()).isEqualTo(1);
            assertThat(instruments.findAll()).filteredOn(instrument -> "Clarinetto".equals(instrument.getName())).hasSize(1);
        });
        transactionExecutor.execute(tenantTwo, () ->
            assertThat(instruments.findAll()).noneMatch(instrument -> "Clarinetto".equals(instrument.getName()))
        );

        long invalidJobId = upload(tenantTwo, UUID.randomUUID(), INVALID_CSV, "invalid.csv");
        awaitStatus(tenantTwo, invalidJobId, OnboardingJobStatus.INVALID);
        mockMvc.perform(get("/api/onboarding/imports/{id}/issues", invalidJobId).with(authentication(tenantTwo, "ROLE_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.content[0].code").value("VALUE_REQUIRED"))
            .andExpect(jsonPath("$.content[0].columnName").value("nome"));
        transactionExecutor.execute(tenantTwo, () -> {
            assertThat(issues.findAllByJob_Id(invalidJobId, Pageable.unpaged()).getContent())
                .extracting(issue -> issue.getCode())
                .contains("VALUE_REQUIRED");
            assertThat(instruments.findAll()).noneMatch(instrument -> "Clarinetto".equals(instrument.getName()));
        });

        // Il conflitto nasce dopo la validazione: il preflight dell'applicazione lo incontra e non cambia nulla.
        long rollbackJobId = uploadWorkbook(tenantOne, rollbackWorkbook());
        awaitStatus(tenantOne, rollbackJobId, OnboardingJobStatus.READY);
        transactionExecutor.execute(tenantOne, this::createConflictingInventoryItem);
        apply(tenantOne, rollbackJobId, UUID.randomUUID());
        awaitStatus(tenantOne, rollbackJobId, OnboardingJobStatus.INVALID);
        transactionExecutor.execute(tenantOne, () -> {
            OnboardingImportJob blocked = jobs.findByIdAndDeletedFalse(rollbackJobId).orElseThrow();
            assertThat(blocked.getStage()).isEqualTo("PREFLIGHT_CONFLICT");
            assertThat(blocked.getLastErrorCode()).isEqualTo("PREFLIGHT_CONFLICT");
            assertThat(issues.findAllByJob_Id(rollbackJobId, Pageable.unpaged()).getContent())
                .extracting(OnboardingImportIssue::getCode)
                .contains("CONFLICT_EXISTING_RECORD");
            assertThat(instruments.findAll()).noneMatch(instrument -> "Strumento rollback".equals(instrument.getName()));
            assertThat(inventory.findAll())
                .filteredOn(item -> "INV-CONFLICT-IT".equals(item.getInventoryNumber()))
                .hasSize(1);
        });
    }

    @Test
    void appliesTheSoundAndTranspositionOfNewInstrumentsAndReportsTheProposals() throws Exception {
        String csv = """
            riferimento,nome,descrizione,suono,trasposizione,trasposizione_chiave_basso
            PLAY-1,Corno in Mib IT,,,,
            PLAY-2,Flicorno tenore IT,,58,-14,-2
            PLAY-3,Rullante IT,,batteria:40,,
            PLAY-4,Zampogna IT,,,,
            """;
        long jobId = upload(playbackTenant, UUID.randomUUID(), csv, "playback.csv");
        awaitStatus(playbackTenant, jobId, OnboardingJobStatus.READY);
        transactionExecutor.execute(playbackTenant, () ->
            assertThat(issues.findAllByJob_Id(jobId, Pageable.unpaged()).getContent())
                .extracting(issue -> issue.getRowNumber() + ":" + issue.getCode())
                .containsExactlyInAnyOrder("2:PLAYBACK_PROPOSED", "5:PLAYBACK_NOT_CONFIGURED")
        );

        apply(playbackTenant, jobId, UUID.randomUUID(), true);
        awaitStatus(playbackTenant, jobId, OnboardingJobStatus.COMPLETED);

        transactionExecutor.execute(playbackTenant, () -> {
            assertThat(instruments.findAll())
                .filteredOn(instrument -> instrument.getName().endsWith(" IT"))
                .extracting(
                    instrument -> instrument.getName(),
                    instrument -> instrument.getTranspositionSemitones(),
                    instrument -> instrument.getBassClefTranspositionSemitones(),
                    instrument -> instrument.getSoundProgram(),
                    instrument -> instrument.getPercussionKey()
                )
                .containsExactlyInAnyOrder(
                    tuple("Corno in Mib IT", -9, null, 60, null),
                    tuple("Flicorno tenore IT", -14, -2, 58, null),
                    tuple("Rullante IT", null, null, null, 40),
                    tuple("Zampogna IT", null, null, null, null)
                );
        });
    }

    /**
     * Il preflight dell'applicazione: il file che non corrisponde più alla sua impronta riporta il job in
     * {@code INVALID} con {@code SOURCE_INTEGRITY_FAILURE} e non tocca nulla all'esterno.
     */
    @Test
    void refusesToApplyAFileThatNoLongerMatchesItsDigest() throws Exception {
        long jobId = upload(limitsTenant, UUID.randomUUID(), instrumentCsv("DIGEST"), "digest.csv");
        awaitStatus(limitsTenant, jobId, OnboardingJobStatus.READY);
        // L'impronta non è aggiornabile da JPA, come il file che descrive: la prova simula il file cambiato sotto.
        jdbcTemplate.update("UPDATE " + schemaOf(limitsTenant) + ".onboarding_import_job SET file_sha256 = ? WHERE id = ?", "0".repeat(64), jobId);

        apply(limitsTenant, jobId, UUID.randomUUID());
        awaitStatus(limitsTenant, jobId, OnboardingJobStatus.INVALID);

        mockMvc.perform(get("/api/onboarding/imports/{id}/issues", jobId).with(authentication(limitsTenant, "ROLE_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].code").value("SOURCE_INTEGRITY_FAILURE"));
        transactionExecutor.execute(limitsTenant, () ->
            assertThat(instruments.findAll()).noneMatch(instrument -> "Clarinetto".equals(instrument.getName()))
        );
        cancelEveryJob(limitsTenant);
    }

    /** Un'altra importazione in applicazione ferma la seconda: il tenant applica una cosa alla volta. */
    @Test
    void refusesASecondApplicationWhileOneIsRunning() throws Exception {
        long running = upload(limitsTenant, UUID.randomUUID(), instrumentCsv("RUN-1"), "running.csv");
        awaitStatus(limitsTenant, running, OnboardingJobStatus.READY);
        long second = upload(limitsTenant, UUID.randomUUID(), instrumentCsv("RUN-2"), "second.csv");
        awaitStatus(limitsTenant, second, OnboardingJobStatus.READY);
        jdbcTemplate.update("UPDATE " + schemaOf(limitsTenant) + ".onboarding_import_job SET status = 'APPLYING' WHERE id = ?", running);

        mockMvc.perform(
            post("/api/onboarding/imports/{id}/apply", second)
                .contentType("application/json")
                .content("{\"warningsAccepted\":false,\"sendSetupEmails\":false}")
                .header("Idempotency-Key", UUID.randomUUID())
                .with(authentication(limitsTenant, "ROLE_ADMIN"))
                .with(csrf())
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("error.onboarding.tenant.busy"));

        cancelEveryJob(limitsTenant);
    }

    /** Oltre il limite di importazioni in attesa dello stesso autore il caricamento è rifiutato. */
    @Test
    void refusesMorePendingUploadsThanTheLimitAllows() throws Exception {
        for (int index = 1; index <= 3; index++) {
            long jobId = upload(limitsTenant, UUID.randomUUID(), instrumentCsv("PEND-" + index), "pending-" + index + ".csv");
            awaitStatus(limitsTenant, jobId, OnboardingJobStatus.READY);
        }

        MockMultipartFile file = new MockMultipartFile("file", "pending-4.csv", "text/csv", instrumentCsv("PEND-4").getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(
            multipart("/api/onboarding/imports")
                .file(file)
                .param("format", "CSV")
                .param("csvSection", "INSTRUMENTS")
                .header("Idempotency-Key", UUID.randomUUID())
                .with(authentication(limitsTenant, "ROLE_ADMIN"))
                .with(csrf())
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("error.onboarding.pendingJobs.tooMany"));

        cancelEveryJob(limitsTenant);
    }

    /** Il polling di una validazione lunga: con lo stesso validatore la risposta è 304 senza corpo. */
    @Test
    void answersNotModifiedWhileTheImportDoesNotMove() throws Exception {
        long jobId = upload(limitsTenant, UUID.randomUUID(), instrumentCsv("ETAG"), "etag.csv");
        awaitStatus(limitsTenant, jobId, OnboardingJobStatus.READY);

        String etag = mockMvc.perform(get("/api/onboarding/imports/{id}", jobId).with(authentication(limitsTenant, "ROLE_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.ETAG);

        mockMvc.perform(get("/api/onboarding/imports/{id}", jobId).header(HttpHeaders.IF_NONE_MATCH, etag).with(authentication(limitsTenant, "ROLE_ADMIN")))
            .andExpect(status().isNotModified())
            .andExpect(header().string(HttpHeaders.ETAG, etag))
            .andExpect(content().string(""));

        cancelEveryJob(limitsTenant);
    }

    /** L'esito chiude il passo 5: conteggi per sezione, identità e durata dell'applicazione. */
    @Test
    void reportsTheOutcomeOfAFinishedImport() throws Exception {
        long jobId = upload(limitsTenant, UUID.randomUUID(), instrumentCsv("OUT-1"), "outcome.csv");
        awaitStatus(limitsTenant, jobId, OnboardingJobStatus.READY);
        apply(limitsTenant, jobId, UUID.randomUUID());
        awaitStatus(limitsTenant, jobId, OnboardingJobStatus.COMPLETED);

        mockMvc.perform(get("/api/onboarding/imports/{id}/outcome", jobId).with(authentication(limitsTenant, "ROLE_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.durationMillis").isNumber())
            .andExpect(jsonPath("$.identitiesCreated").value(0))
            .andExpect(jsonPath("$.identitiesLinked").value(0))
            .andExpect(jsonPath("$.sections[0].section").value("INSTRUMENTS"))
            .andExpect(jsonPath("$.sections[0].create").value(1));
    }

    /** Nessun endpoint dell'onboarding risponde a chi non amministra il tenant. */
    @Test
    void refusesEveryEndpointToWhoeverDoesNotAdministerTheTenant() throws Exception {
        long jobId = upload(limitsTenant, UUID.randomUUID(), instrumentCsv("SEC-1"), "security.csv");
        awaitStatus(limitsTenant, jobId, OnboardingJobStatus.READY);

        for (String path : new String[] {
            "/api/onboarding/context",
            "/api/onboarding/templates/xlsx",
            "/api/onboarding/imports",
            "/api/onboarding/imports/" + jobId,
            "/api/onboarding/imports/" + jobId + "/outcome",
            "/api/onboarding/imports/" + jobId + "/sections",
            "/api/onboarding/imports/" + jobId + "/rows",
            "/api/onboarding/imports/" + jobId + "/issues",
            "/api/onboarding/imports/" + jobId + "/validation-report"
        })
            mockMvc.perform(get(path).with(authentication(limitsTenant, "ROLE_USER"))).andExpect(status().isForbidden());

        mockMvc.perform(
            post("/api/onboarding/imports/{id}/apply", jobId)
                .contentType("application/json")
                .content("{\"warningsAccepted\":false,\"sendSetupEmails\":false}")
                .header("Idempotency-Key", UUID.randomUUID())
                .with(authentication(limitsTenant, "ROLE_USER"))
                .with(csrf())
        ).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/onboarding/imports/{id}", jobId).with(authentication(limitsTenant, "ROLE_USER")).with(csrf()))
            .andExpect(status().isForbidden());

        cancelEveryJob(limitsTenant);
    }

    /**
     * Il riavvio in ogni fase della saga: il job lasciato in volo e fermo da più del tempo minimo viene ripreso
     * dalla ricognizione, che lo porta allo stato finale invece di lasciarlo bloccato.
     */
    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    void resumesAnImportLeftInFlightByARestart() throws Exception {
        long jobId = upload(limitsTenant, UUID.randomUUID(), instrumentCsv("RES-1"), "restart.csv");
        awaitStatus(limitsTenant, jobId, OnboardingJobStatus.READY);
        leaveInFlight(jobId, OnboardingJobStatus.VALIDATING, "VALIDATING_ROWS");

        recovery.resumeUploadedJobs();
        awaitStatus(limitsTenant, jobId, OnboardingJobStatus.READY);

        apply(limitsTenant, jobId, UUID.randomUUID());
        awaitStatus(limitsTenant, jobId, OnboardingJobStatus.COMPLETED);
        leaveInFlight(jobId, OnboardingJobStatus.APPLYING, "PREPARING_IDENTITIES");

        recovery.resumeUploadedJobs();
        awaitStatus(limitsTenant, jobId, OnboardingJobStatus.COMPLETED);

        leaveInFlight(jobId, OnboardingJobStatus.COMPENSATING, "COMPENSATING_IDENTITIES");

        recovery.resumeUploadedJobs();
        awaitStatus(limitsTenant, jobId, OnboardingJobStatus.FAILED);
        cancelEveryJob(limitsTenant);
    }

    /** Il carico della specifica: 5.000 righe validate e applicate entro l'obiettivo di rilascio. */
    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void importsFiveThousandRowsWithinTheReleaseTarget() throws Exception {
        StringBuilder csv = new StringBuilder("riferimento,nome,descrizione\r\n");
        for (int row = 1; row <= 5_000; row++) csv.append("LOAD-").append(row).append(",Strumento di carico ").append(row).append(",\r\n");

        long jobId = upload(loadTenant, UUID.randomUUID(), csv.toString(), "load.csv");
        awaitStatus(loadTenant, jobId, OnboardingJobStatus.READY, Duration.ofMinutes(3));
        transactionExecutor.execute(loadTenant, () ->
            assertThat(jobs.findByIdAndDeletedFalse(jobId).orElseThrow().getTotalRows()).isEqualTo(5_000)
        );

        apply(loadTenant, jobId, UUID.randomUUID());
        awaitStatus(loadTenant, jobId, OnboardingJobStatus.COMPLETED, Duration.ofMinutes(5));

        transactionExecutor.execute(loadTenant, () ->
            assertThat(instruments.findAll()).filteredOn(instrument -> instrument.getName().startsWith("Strumento di carico ")).hasSize(5_000)
        );
    }

    /**
     * Riporta il job allo stato in volo che un riavvio avrebbe lasciato, fermo da più del tempo minimo. La scrittura
     * passa da SQL perché l'audit riscriverebbe {@code edit_date}, che è proprio il tempo di inattività osservato.
     */
    private void leaveInFlight(long jobId, OnboardingJobStatus status, String stage) {
        jdbcTemplate.update(
            "UPDATE " + schemaOf(limitsTenant) + ".onboarding_import_job" +
                " SET status = ?, stage = ?, resume_claimed_at = NULL, completed_at = NULL, edit_date = ? WHERE id = ?",
            status.name(),
            stage,
            Timestamp.from(Instant.now().minus(Duration.ofMinutes(10))),
            jobId
        );
    }

    /** Libera il tenant delle prove sui limiti: le importazioni in attesa contano per l'utente che le ha caricate. */
    private void cancelEveryJob(String tenant) {
        jdbcTemplate.update(
            "UPDATE " + schemaOf(tenant) + ".onboarding_import_job SET status = 'CANCELLED', deleted = TRUE WHERE deleted = FALSE"
        );
    }

    /** Uno strumento che nessun'altra prova importa: i dati applicati restano nel tenant e darebbero avvisi. */
    private String instrumentCsv(String reference) {
        return "riferimento,nome,descrizione\r\n" + reference + ",Strumento " + reference + ",\r\n";
    }

    private String schemaOf(String tenant) {
        return schemaNames.resolve(tenant);
    }

    private long upload(String tenant, UUID key, String csv, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        String response = mockMvc
            .perform(
                multipart("/api/onboarding/imports")
                    .file(file)
                    .param("format", "CSV")
                    .param("csvSection", "INSTRUMENTS")
                    .header("Idempotency-Key", key)
                    .with(authentication(tenant, "ROLE_ADMIN"))
                    .with(csrf())
            )
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response).path("id").asLong();
    }

    private void apply(String tenant, long jobId, UUID key) throws Exception {
        apply(tenant, jobId, key, false);
    }

    private void apply(String tenant, long jobId, UUID key, boolean warningsAccepted) throws Exception {
        mockMvc.perform(
            post("/api/onboarding/imports/{id}/apply", jobId)
                .contentType("application/json")
                .content("{\"warningsAccepted\":" + warningsAccepted + ",\"sendSetupEmails\":false}")
                .header("Idempotency-Key", key)
                .with(authentication(tenant, "ROLE_ADMIN"))
                .with(csrf())
        ).andExpect(status().isAccepted());
    }

    private long uploadWorkbook(String tenant, byte[] workbook) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "rollback.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workbook
        );
        String response = mockMvc
            .perform(
                multipart("/api/onboarding/imports")
                    .file(file)
                    .param("format", "XLSX")
                    .param("selectedSections", "INSTRUMENTS", "INVENTORY")
                    .header("Idempotency-Key", UUID.randomUUID())
                    .with(authentication(tenant, "ROLE_ADMIN"))
                    .with(csrf())
            )
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response).path("id").asLong();
    }

    private byte[] rollbackWorkbook() throws Exception {
        try (
            XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(templates.xlsx()));
            ByteArrayOutputStream output = new ByteArrayOutputStream()
        ) {
            writeRow(workbook.getSheet("Strumenti"), "STR-ROLLBACK", "Strumento rollback", "Deve essere annullato");
            writeRow(workbook.getSheet("Inventario"), "INV-CONFLICT-IT", "Custodia", "", "1", "", "", "GOOD", "");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void writeRow(Sheet sheet, String... values) {
        Row row = sheet.createRow(1);
        for (int column = 0; column < values.length; column++) row.createCell(column).setCellValue(values[column]);
    }

    private void createConflictingInventoryItem() {
        InventoryItem item = new InventoryItem();
        item.initializeAudit(ACTOR);
        item.setInventoryNumber("INV-CONFLICT-IT");
        item.setName("Custodia esistente");
        item.setTotalQuantity(1);
        item.setConditionStatus(InventoryCondition.GOOD);
        item.setQrPublicId(UUID.randomUUID());
        item.setQrVersion(1);
        item.setQrIssuedAt(ZonedDateTime.now());
        item.setQrIssuedBy(ACTOR);
        inventory.save(item);
    }

    private void awaitStatus(String tenant, long jobId, OnboardingJobStatus expected) throws InterruptedException {
        awaitStatus(tenant, jobId, expected, Duration.ofSeconds(15));
    }

    private void awaitStatus(String tenant, long jobId, OnboardingJobStatus expected, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        OnboardingJobStatus actual = null;
        while (Instant.now().isBefore(deadline)) {
            actual = transactionExecutor.execute(tenant, () -> jobs.findByIdAndDeletedFalse(jobId).orElseThrow().getStatus());
            if (actual == expected) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Expected onboarding job " + jobId + " to reach " + expected + " but was " + actual);
    }

    private RequestPostProcessor authentication(String tenant, String role) {
        return jwt()
            .jwt(builder -> builder.subject(ACTOR).claim("tenant", tenant).claim("preferred_username", ACTOR))
            .authorities(new SimpleGrantedAuthority(role));
    }

    private void createTenant(String code) {
        Date now = new Date();
        Tenants tenant = new Tenants();
        tenant.setName(code);
        tenant.setCode(code);
        tenant.setActive(true);
        tenant.setMaxUsers(100L);
        tenant.setFinanceEnabled(true);
        tenant.setInventoryEnabled(true);
        tenant.setOnboardingImportEnabled(true);
        tenant.setDeleted(false);
        tenant.setInsertBy(ACTOR);
        tenant.setInsertDate(now);
        tenant.setEditBy(ACTOR);
        tenant.setEditDate(now);
        tenant.setEntityVersion(0L);
        tenantsRepository.save(tenant);
    }
}
