package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActStatus;
import com.fundaro.zodiac.taurus.domain.signature.SignatureMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaNameResolver;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryDecisionRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryItemRequest;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.*;
import com.fundaro.zodiac.taurus.service.impl.InventoryService;
import com.fundaro.zodiac.taurus.service.loansheet.InventoryLoanTemplateService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Acknowledgment of assignments confirmed by a code on PostgreSQL: decisions, acts, evidence chain and its
 * verification, and the removal of the personal data by the GDPR erasure.
 */
@IntegrationTest
@TestPropertySource(
    properties = {
        "application.base-path=target/signature-it-storage",
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
        "application.signature.otp-secret=integration-secret",
        "application.signature.seal.ephemeral=true",
    }
)
class SignatureIT {

    private static final Pattern CODE = Pattern.compile("Codice di conferma: (\\d{6})");

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private TenantSchemaProvisioningService provisioningService;

    @Autowired
    private TenantSchemaNameResolver schemaNameResolver;

    @Autowired
    private TenantTransactionExecutor transactionExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryLoanTemplateService templateService;

    @Autowired
    private SignatureChallengeService challengeService;

    @Autowired
    private SignatureEvidenceService evidenceService;

    @Autowired
    private SignatureErasureService erasureService;

    private final String tenantCode = "signature-it-" + UUID.randomUUID();
    private final String memberKey = "signature-member-" + UUID.randomUUID();
    private final List<Long> identityIds = new ArrayList<>();
    private long memberIndex;
    private Long tenantId;

    @BeforeEach
    void provisionTenant() {
        provisioningService.provision(tenantCode);
        tenantId = transactionExecutor.execute(
            TenantSchemaNameResolver.DEFAULT_SCHEMA,
            () -> jdbcTemplate.queryForObject(
                "INSERT INTO public.tenant (deleted, name, code, active) VALUES (false, 'Banda musicale di prova', ?, true) RETURNING id",
                Long.class,
                tenantCode
            )
        );
        provisioningService.linkTenant(tenantId, tenantCode);
        memberIndex = createMember(memberKey, "mario.rossi@example.org");
    }

    @AfterEach
    void dropTenant() {
        try {
            provisioningService.dropSchema(tenantCode);
        } finally {
            transactionExecutor.execute(TenantSchemaNameResolver.DEFAULT_SCHEMA, () -> {
                identityIds.forEach(id -> jdbcTemplate.update("DELETE FROM public.user_identity WHERE id = ?", id));
                jdbcTemplate.update("UPDATE public.tenant_schema_registry SET tenant_id = NULL WHERE tenant_code = ?", tenantCode);
                jdbcTemplate.update("DELETE FROM public.tenant WHERE id = ?", tenantId);
            });
        }
    }

    @Test
    void confirmsAcknowledgmentsWithACodeAndKeepsTheEvidenceVerifiable() {
        JwtAuthenticationToken admin = token("admin-1");
        JwtAuthenticationToken member = token(memberKey);
        long version = tx(() -> templateService.overview(admin).settingsVersion());
        assertThat(tx(() -> templateService.updateSignature(new SettingsUpdate(true, version), admin)).signature().enabled()).isTrue();
        InventoryAssignmentDTO clarinet = assign("INV-1", "Clarinetto", admin);
        InventoryAssignmentDTO uniform = assign("INV-2", "Divisa", admin);

        assertThatThrownBy(() -> tx(() -> inventoryService.decide(clarinet.id(), decision(clarinet, InventoryDecisionType.ACCEPTED, null), member)))
            .hasFieldOrPropertyWithValue("errorKey", "inventory.decision.signatureRequired");

        Challenge challenge = inTenant(() -> challengeService.create(new ChallengeRequest(
            SignaturePurpose.ASSIGNMENT_DECISION,
            SignatureMethod.EMAIL_OTP,
            List.of(
                new ChallengeItem(clarinet.id(), clarinet.revisionHash(), InventoryDecisionType.ACCEPTED, null),
                new ChallengeItem(uniform.id(), uniform.revisionHash(), InventoryDecisionType.REJECTED, "Manca la cintura")
            )
        ), http(), member));
        String code = sentCode();
        assertThat(challenge.declarations()).hasSize(2);
        assertThat(jdbcTemplate.queryForObject("SELECT code_digest FROM " + schema() + ".signature_challenge WHERE id = ?", String.class, challenge.id()))
            .hasSize(64)
            .isNotEqualTo(code);

        String wrong = code.equals("000000") ? "000001" : "000000";
        assertThatThrownBy(() -> inTenant(() -> challengeService.confirm(challenge.id(), new ConfirmRequest(wrong), http(), member)))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> assertThat(exception.getErrorKey()).isEqualTo("signature.challenge.invalidCode"));
        assertThat(jdbcTemplate.queryForObject("SELECT attempts FROM " + schema() + ".signature_challenge WHERE id = ?", Integer.class, challenge.id()))
            .isEqualTo(1);

        Confirmation confirmation = inTenant(() -> challengeService.confirm(challenge.id(), new ConfirmRequest(code), http(), member));

        assertThat(confirmation.acts()).extracting(ActOutcome::status).containsOnly(SignatureActStatus.SIGNED);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + schema() + ".inventory_assignment_decision WHERE signature_evidence_id IS NOT NULL", Long.class
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT string_agg(signer_name || '|' || declaration_text, ';' ORDER BY sequence) FROM " + schema() + ".signature_evidence", String.class
        )).contains("Mario Rossi|Ho preso visione").contains("li rifiuto per il seguente motivo: Manca la cintura");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM " + schema() + ".signature_challenge WHERE id = ?", String.class, challenge.id()))
            .isEqualTo("CONFIRMED");
        assertThat(tx(() -> evidenceService.verify())).isEqualTo(new ChainVerification(true, 2, null, null));

        jdbcTemplate.execute("ALTER TABLE " + schema() + ".signature_evidence DISABLE TRIGGER trg_signature_evidence_guard");
        jdbcTemplate.update("UPDATE " + schema() + ".signature_evidence SET signer_name = 'Altra persona' WHERE sequence = 2");
        jdbcTemplate.execute("ALTER TABLE " + schema() + ".signature_evidence ENABLE TRIGGER trg_signature_evidence_guard");
        assertThat(tx(() -> evidenceService.verify())).isEqualTo(new ChainVerification(false, 1, 2L, "personalData"));
        jdbcTemplate.execute("ALTER TABLE " + schema() + ".signature_evidence DISABLE TRIGGER trg_signature_evidence_guard");
        jdbcTemplate.update("UPDATE " + schema() + ".signature_evidence SET signer_name = 'Mario Rossi' WHERE sequence = 2");
        jdbcTemplate.execute("ALTER TABLE " + schema() + ".signature_evidence ENABLE TRIGGER trg_signature_evidence_guard");

        transactionExecutor.execute(tenantCode, () -> erasureService.purgeForUser(memberKey, "erased:1", "admin-1"));

        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + schema() + ".signature_evidence WHERE purged_at IS NOT NULL AND signer_name IS NULL AND destination_email IS NULL",
            Long.class
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + schema() + ".signature_challenge", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + schema() + ".signature_act_signer WHERE user_keycloak_id = 'erased:1'", Long.class
        )).isEqualTo(2);
        assertThat(tx(() -> evidenceService.verify())).isEqualTo(new ChainVerification(true, 2, null, null));
    }

    private InventoryAssignmentDTO assign(String number, String name, JwtAuthenticationToken admin) {
        long itemId = tx(() -> inventoryService.createItem(new InventoryItemRequest(
            number, name, null, 1, BigDecimal.TEN, "EUR", InventoryCondition.GOOD, null, null, null, InventoryLoanType.PRECARIOUS
        ), admin).id());
        return tx(() -> inventoryService.assign(itemId, new InventoryAssignmentRequest(memberIndex, 0, 1, null, null, null), admin));
    }

    private static InventoryDecisionRequest decision(InventoryAssignmentDTO assignment, InventoryDecisionType type, String reason) {
        return new InventoryDecisionRequest(type, reason, assignment.revisionHash());
    }

    private String sentCode() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        Matcher matcher = CODE.matcher(captor.getValue().getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private long createMember(String keycloakId, String email) {
        Long identityId = transactionExecutor.execute(
            TenantSchemaNameResolver.DEFAULT_SCHEMA,
            () -> jdbcTemplate.queryForObject(
                "INSERT INTO public.user_identity (keycloak_id, created_at) VALUES (?, CURRENT_TIMESTAMP) RETURNING id",
                Long.class,
                keycloakId
            )
        );
        identityIds.add(identityId);
        return jdbcTemplate.queryForObject(
            "INSERT INTO " + schema() + ".app_user (user_identity_id, keycloak_id, name, last_name, email) VALUES (?, ?, ?, ?, ?) RETURNING id",
            Long.class,
            identityId,
            keycloakId,
            "Mario",
            "Rossi",
            email
        );
    }

    private <T> T tx(java.util.function.Supplier<T> action) {
        return transactionExecutor.execute(tenantCode, action);
    }

    private <T> T inTenant(java.util.function.Supplier<T> action) {
        return TenantContext.call(tenantCode, action);
    }

    private String schema() {
        return '"' + schemaNameResolver.resolve(tenantCode).replace("\"", "\"\"") + '"';
    }

    private static MockHttpServletRequest http() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("User-Agent", "Mozilla/5.0");
        return request;
    }

    private JwtAuthenticationToken token(String subject) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).claim("tenant", tenantCode).claim("sid", "session-1")
            .claim("given_name", "Utente").claim("family_name", "Di prova")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
