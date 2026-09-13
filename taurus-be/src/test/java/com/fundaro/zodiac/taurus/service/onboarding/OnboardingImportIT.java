package com.fundaro.zodiac.taurus.service.onboarding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.TaurusApp;
import com.fundaro.zodiac.taurus.config.EmbeddedSQL;
import com.fundaro.zodiac.taurus.config.JacksonConfiguration;
import com.fundaro.zodiac.taurus.config.TestSecurityConfiguration;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingJobStatus;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = {TaurusApp.class, JacksonConfiguration.class, TestSecurityConfiguration.class})
@EmbeddedSQL
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(
    properties = {
        "application.base-path=target/test-data/onboarding-import-it",
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
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

    private final String tenantOne = "onboarding-a-" + UUID.randomUUID();
    private final String tenantTwo = "onboarding-b-" + UUID.randomUUID();

    @BeforeAll
    void provisionTenants() {
        createTenant(tenantOne);
        createTenant(tenantTwo);
        provisioningService.provision(tenantOne);
        provisioningService.provision(tenantTwo);
        provisioningService.linkTenant(tenantsRepository.findByCodeAndDeletedFalse(tenantOne).orElseThrow().getId(), tenantOne);
        provisioningService.linkTenant(tenantsRepository.findByCodeAndDeletedFalse(tenantTwo).orElseThrow().getId(), tenantTwo);
        when(keycloakService.getGroupIdByName(tenantOne)).thenReturn("group-a");
    }

    @AfterAll
    void cleanup() throws Exception {
        storageService.deleteTenantDirectory(tenantOne);
        storageService.deleteTenantDirectory(tenantTwo);
        provisioningService.dropSchema(tenantOne);
        provisioningService.dropSchema(tenantTwo);
        jdbcTemplate.update("DELETE FROM public.tenant WHERE code IN (?, ?)", tenantOne, tenantTwo);
    }

    @Test
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

        long rollbackJobId = uploadWorkbook(tenantOne, rollbackWorkbook());
        awaitStatus(tenantOne, rollbackJobId, OnboardingJobStatus.READY);
        transactionExecutor.execute(tenantOne, this::createConflictingInventoryItem);
        apply(tenantOne, rollbackJobId, UUID.randomUUID());
        awaitStatus(tenantOne, rollbackJobId, OnboardingJobStatus.FAILED);
        transactionExecutor.execute(tenantOne, () -> {
            assertThat(instruments.findAll()).noneMatch(instrument -> "Strumento rollback".equals(instrument.getName()));
            assertThat(inventory.findAll())
                .filteredOn(item -> "INV-CONFLICT-IT".equals(item.getInventoryNumber()))
                .hasSize(1);
        });
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
        mockMvc.perform(
            post("/api/onboarding/imports/{id}/apply", jobId)
                .contentType("application/json")
                .content("{\"warningsAccepted\":false,\"sendSetupEmails\":false}")
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
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
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
