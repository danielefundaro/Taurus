package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanHolderRole;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanReturnOutcome;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSheetStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanSignatureMode;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActStatus;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.domain.signature.SignatureMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaNameResolver;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryAssignmentRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryItemRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.CloseRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.Detail;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.IssueRequest;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.Proposal;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryReturnDTO;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryReturnRequest;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.*;
import com.fundaro.zodiac.taurus.service.impl.DataErasureService;
import com.fundaro.zodiac.taurus.service.impl.InventoryService;
import com.fundaro.zodiac.taurus.service.loansheet.InventoryLoanSheetService;
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * A loan sheet signed electronically on PostgreSQL: issue with the data of the holder, distinct signatures of the sheet
 * and of the clauses, signature for the association, seal and attestation, variations, return report that settles
 * the sheet, dossier and removal at the end of the grace period. A parent or guardian signs from the link sent by
 * e-mail, without an account. The holder can sign a document with CieSign, verified with a test certification
 * authority configured as trust anchor.
 */
@IntegrationTest
@TestPropertySource(
    properties = {
        "application.base-path=target/loan-sheet-signature-it-storage",
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
        "application.signature.otp-secret=integration-secret",
        "application.signature.seal.ephemeral=true",
    }
)
class LoanSheetSignatureIT {

    private static final Pattern CODE = Pattern.compile("Codice di conferma: (\\d{6})");
    private static final Pattern LINK = Pattern.compile("/firma/([A-Za-z0-9_-]{43})");

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
    private InventoryLoanSheetService loanSheetService;

    @Autowired
    private InventoryLoanTemplateService templateService;

    @Autowired
    private LoanSheetSignatureService signatureService;

    @Autowired
    private SignatureChallengeService challengeService;

    @Autowired
    private SignatureEvidenceService evidenceService;

    @Autowired
    private SignatureLinkResolver linkResolver;

    @Autowired
    private SignatureLinkAdministration linkAdministration;

    @Autowired
    private CieSignService cieSignService;

    @Autowired
    private CieSignVerifier cieSignVerifier;

    @Autowired
    private DataErasureService dataErasureService;

    private static CieSignTestSupport.Authority cie;

    private final String tenantCode = "loan-sheet-signature-it-" + UUID.randomUUID();
    private final String memberKey = "member-" + UUID.randomUUID();
    private final String adminKey = "admin-" + UUID.randomUUID();
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
        memberIndex = createUser(memberKey, "Mario", "Rossi", "mario.rossi@example.org", "ROLE_USER");
        createUser(adminKey, "Anna", "Bianchi", "anna.bianchi@example.org", "ROLE_ADMIN");
        if (cie == null) cie = CieSignTestSupport.authority("CIE di prova");
        cieSignVerifier.useTrustAnchors(List.of(cie.root().certificate()));
    }

    @AfterEach
    void dropTenant() {
        cieSignVerifier.useTrustAnchors(List.of());
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
    void signsTheActsOfASheetAndRemovesThemAfterTheGracePeriod() throws IOException {
        JwtAuthenticationToken admin = token(adminKey);
        JwtAuthenticationToken member = token(memberKey);
        long version = tx(() -> templateService.overview(admin).settingsVersion());
        tx(() -> templateService.updateSignature(new SettingsUpdate(true, version), admin));
        InventoryAssignmentDTO clarinet = assign("INV-1", "Clarinetto", admin);

        Proposal proposal = tx(() -> loanSheetService.proposal(memberIndex, admin));
        assertThat(proposal.signature().enabled()).isTrue();
        assertThat(proposal.signature().templateSignable()).isTrue();
        assertThat(proposal.signature().associationSigners()).extracting(AssociationSignerOption::userId).containsExactly(adminKey);
        assertThat(proposal.signature().defaultAssociationSignerId()).isEqualTo(adminKey);

        IssueRequest request = new IssueRequest(
            List.of(clarinet.id()), LocalDate.now(), "Roma", InventoryLoanHolderRole.MEMBER, null, null, null, InventoryLoanSignatureMode.ELECTRONIC, memberKey
        );
        assertThatThrownBy(() -> tx(() -> loanSheetService.issue(memberIndex, request, admin)))
            .hasFieldOrPropertyWithValue("errorKey", "signature.associationSigner.sameAsHolder");
        Detail issued = tx(() -> loanSheetService.issue(memberIndex, new IssueRequest(
            List.of(clarinet.id()), LocalDate.now(), "Roma", InventoryLoanHolderRole.MEMBER, null, null, null, InventoryLoanSignatureMode.ELECTRONIC, adminKey
        ), admin));
        long sheetId = issued.summary().id();
        assertThat(issued.summary().signatureMode()).isEqualTo(InventoryLoanSignatureMode.ELECTRONIC);
        assertThat(issued.associationSignatoryName()).isEqualTo("Anna Bianchi");

        Act issue = single(tx(() -> signatureService.ownActs(member)));
        assertThat(issue.type()).isEqualTo(SignatureActType.LOAN_SHEET_ISSUE);
        assertThat(issue.dataRequired()).isTrue();
        // The dashboard asks the signatures of the acts waiting for each user, as the box of the signatures lists them.
        assertThat(tx(() -> signatureService.countAwaitingUser(memberKey))).isEqualTo(1);
        assertThat(tx(() -> signatureService.countAwaitingUser(adminKey))).isZero();
        assertThatThrownBy(() -> tx(() -> signatureService.submitHolderData(issue.id(), holderData("RSSMRA85T10A562X"), member)))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("signature.holderData.invalid");
                assertThat(exception.getArgs().values()).containsEntry("fields", List.of("taxCode"));
            });
        Act withData = tx(() -> signatureService.submitHolderData(issue.id(), holderData("rssmra85t10a562s"), member));
        assertThat(withData.status()).isEqualTo(SignatureActStatus.AWAITING_SIGNATURES);
        assertThat(withData.documents()).extracting(ActDocument::role).containsExactly(SignatureDocumentRole.MAIN, SignatureDocumentRole.CLAUSES);
        assertThat(withData.pendingPurposes()).containsExactly(SignaturePurpose.ACCEPTANCE, SignaturePurpose.CLAUSE_APPROVAL);
        assertThat(jdbcTemplate.queryForObject("SELECT holder_tax_code FROM " + schema() + ".inventory_loan_sheet WHERE id = ?", String.class, sheetId))
            .isEqualTo("RSSMRA85T10A562S");

        Challenge acceptance = challenge(SignaturePurpose.ACCEPTANCE, withData, SignatureDocumentRole.MAIN, member);
        assertThat(acceptance.declarations()).singleElement().asString().contains("foglio di consegna e affidamento in comodato n. ").contains("lo accetto");
        confirm(acceptance, member);
        confirm(challenge(SignaturePurpose.CLAUSE_APPROVAL, withData, SignatureDocumentRole.CLAUSES, member), member);
        assertThatThrownBy(() -> inTenant(() -> challengeService.create(request(SignaturePurpose.ACCEPTANCE, withData, SignatureDocumentRole.MAIN), http(), member)))
            .hasFieldOrPropertyWithValue("errorKey", "signature.act.alreadySigned");

        Act awaitingAssociation = tx(() -> signatureService.ownAct(issue.id(), admin));
        assertThat(awaitingAssociation.pendingPurposes()).containsExactly(SignaturePurpose.ASSOCIATION_SIGNATURE);
        assertThat(tx(() -> signatureService.countAwaitingUser(memberKey))).isZero();
        assertThat(tx(() -> signatureService.countAwaitingUser(adminKey))).isEqualTo(1);
        assertThat(awaitingAssociation.associationSignerChangeable()).isFalse();
        confirm(challenge(SignaturePurpose.ASSOCIATION_SIGNATURE, awaitingAssociation, SignatureDocumentRole.MAIN, admin), admin);

        Act signed = tx(() -> signatureService.ownAct(issue.id(), member));
        assertThat(signed.status()).isEqualTo(SignatureActStatus.SIGNED);
        assertThat(tx(() -> signatureService.countAwaitingUser(adminKey))).isZero();
        assertThat(signed.attestationAvailable()).isTrue();
        assertThat(signed.documents()).allSatisfy(document -> assertThat(document.sealedHash()).hasSize(64).isNotEqualTo(document.frozenHash()));
        ActFile sealed = tx(() -> signatureService.document(issue.id(), SignatureDocumentRole.MAIN, false, member));
        assertThat(SignatureSupport.sha256(sealed.bytes())).isEqualTo(signed.documents().get(0).sealedHash());
        assertThat(new String(sealed.bytes(), StandardCharsets.ISO_8859_1)).contains("/ByteRange");
        assertThat(tx(() -> signatureService.attestation(issue.id(), true, admin)).bytes()).isNotEmpty();
        assertThat(tx(() -> evidenceService.verify())).isEqualTo(new ChainVerification(true, 3, null, null));

        InventoryAssignmentDTO stand = assign("INV-2", "Leggio", admin);
        Act delivery = tx(() -> signatureService.sheetActs(sheetId, admin)).stream()
            .filter(act -> act.type() == SignatureActType.LOAN_SHEET_VARIATION).findFirst().orElseThrow();
        assertThat(delivery.operation()).isEqualTo("Consegna successiva: Leggio");
        assertThat(delivery.status()).isEqualTo(SignatureActStatus.AWAITING_SIGNATURES);

        completeReturn(clarinet, admin, member);
        completeReturn(stand, admin, member);
        assertThat(tx(() -> signatureService.sheetActs(sheetId, admin))).extracting(Act::operation)
            .containsExactly("Emissione del foglio", "Consegna successiva: Leggio", "Restituzione parziale: Clarinetto");

        tx(() -> loanSheetService.close(sheetId, new CloseRequest(InventoryLoanReturnOutcome.COMPLIANT, null, null, null), admin));
        Act returnAct = tx(() -> signatureService.ownActs(member)).stream()
            .filter(act -> act.type() == SignatureActType.LOAN_SHEET_RETURN).findFirst().orElseThrow();
        confirm(challenge(SignaturePurpose.ACCEPTANCE, returnAct, SignatureDocumentRole.MAIN, member), member);
        confirm(challenge(SignaturePurpose.ASSOCIATION_SIGNATURE, returnAct, SignatureDocumentRole.MAIN, admin), admin);

        assertThat(tx(() -> loanSheetService.detail(sheetId, admin)).summary().status()).isEqualTo(InventoryLoanSheetStatus.SETTLED);
        List<Act> acts = tx(() -> signatureService.sheetActs(sheetId, admin));
        assertThat(acts).extracting(Act::status).containsExactly(
            SignatureActStatus.SIGNED,
            SignatureActStatus.CANCELLED,
            SignatureActStatus.CANCELLED,
            SignatureActStatus.SIGNED
        );
        assertThat(acts.get(0).purgeAfter()).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT holder_tax_code FROM " + schema() + ".inventory_loan_sheet WHERE id = ?", String.class, sheetId)).isNull();

        List<String> entries = zipEntries(tx(() -> signatureService.dossier(sheetId, admin)).bytes());
        assertThat(entries).contains("INDICE.txt");
        assertThat(entries).filteredOn(name -> name.startsWith("01-emissione/")).hasSize(3);
        assertThat(entries).filteredOn(name -> name.startsWith("02-riconsegna/")).hasSize(2);

        jdbcTemplate.update("UPDATE " + schema() + ".signature_act SET purge_after = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE purge_after IS NOT NULL");
        assertThat(tx(() -> signatureService.purgeDue(java.time.ZonedDateTime.now(), tenantCode, "test"))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + schema() + ".signature_document WHERE frozen_media_asset_id IS NOT NULL OR sealed_media_asset_id IS NOT NULL", Long.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + schema() + ".signature_evidence WHERE purged_at IS NULL", Long.class)).isZero();
        assertThatThrownBy(() -> tx(() -> signatureService.dossier(sheetId, admin))).hasFieldOrPropertyWithValue("errorKey", "signature.act.purged");
        assertThat(tx(() -> evidenceService.verify()).valid()).isTrue();
    }

    @Test
    void theGdprErasureOfAMemberWithNothingToReturnRemovesTheSignedActsAtOnce() {
        JwtAuthenticationToken admin = token(adminKey);
        JwtAuthenticationToken member = token(memberKey);
        long version = tx(() -> templateService.overview(admin).settingsVersion());
        tx(() -> templateService.updateSignature(new SettingsUpdate(true, version), admin));
        InventoryAssignmentDTO clarinet = assign("INV-1", "Clarinetto", admin);
        long sheetId = tx(() -> loanSheetService.issue(memberIndex, new IssueRequest(
            List.of(clarinet.id()), LocalDate.now(), "Roma", InventoryLoanHolderRole.MEMBER, null, null, null, InventoryLoanSignatureMode.ELECTRONIC, adminKey
        ), admin)).summary().id();
        Act issue = tx(() -> signatureService.submitHolderData(single(tx(() -> signatureService.ownActs(member))).id(), holderData("RSSMRA85T10A562S"), member));
        confirm(challenge(SignaturePurpose.ACCEPTANCE, issue, SignatureDocumentRole.MAIN, member), member);
        confirm(challenge(SignaturePurpose.CLAUSE_APPROVAL, issue, SignatureDocumentRole.CLAUSES, member), member);
        confirm(challenge(SignaturePurpose.ASSOCIATION_SIGNATURE, tx(() -> signatureService.ownAct(issue.id(), admin)), SignatureDocumentRole.MAIN, admin), admin);
        completeReturn(clarinet, admin, member);
        tx(() -> loanSheetService.close(sheetId, new CloseRequest(InventoryLoanReturnOutcome.COMPLIANT, null, null, null), admin));
        Act returnAct = tx(() -> signatureService.ownActs(member)).stream()
            .filter(act -> act.type() == SignatureActType.LOAN_SHEET_RETURN).findFirst().orElseThrow();
        confirm(challenge(SignaturePurpose.ACCEPTANCE, returnAct, SignatureDocumentRole.MAIN, member), member);
        confirm(challenge(SignaturePurpose.ASSOCIATION_SIGNATURE, returnAct, SignatureDocumentRole.MAIN, admin), admin);
        assertThat(tx(() -> loanSheetService.detail(sheetId, admin)).summary().status()).isEqualTo(InventoryLoanSheetStatus.SETTLED);

        boolean deferred = dataErasureService.requestInventoryAwareErasure(memberKey, memberIndex, tenantCode, "Mario Rossi", "mario.rossi@example.org", adminKey);

        assertThat(deferred).isFalse();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + schema() + ".signature_document WHERE frozen_media_asset_id IS NOT NULL OR sealed_media_asset_id IS NOT NULL", Long.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + schema() + ".signature_act WHERE purged_at IS NULL", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + schema() + ".signature_evidence WHERE purged_at IS NULL OR signer_name IS NOT NULL OR declaration_text IS NOT NULL", Long.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + schema() + ".signature_act_signer WHERE user_keycloak_id = ?", Long.class, memberKey))
            .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT name || ' ' || last_name FROM " + schema() + ".app_user WHERE id = ?", String.class, memberIndex))
            .isEqualTo("Utente eliminato");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + schema() + ".app_user_role WHERE user_id = ? AND deleted = false", Long.class, memberIndex))
            .isZero();
        assertThatThrownBy(() -> tx(() -> signatureService.dossier(sheetId, admin))).hasFieldOrPropertyWithValue("errorKey", "signature.act.purged");
        assertThat(tx(() -> evidenceService.verify()).valid()).isTrue();
    }

    @Test
    void changesTheSignerForTheAssociationAndDeclaresAnActSignedOnPaper() {
        JwtAuthenticationToken admin = token(adminKey);
        JwtAuthenticationToken member = token(memberKey);
        String otherAdmin = "admin-" + UUID.randomUUID();
        createUser(otherAdmin, "Luca", "Verdi", "luca.verdi@example.org", "ROLE_SUPER_ADMIN");
        long version = tx(() -> templateService.overview(admin).settingsVersion());
        tx(() -> templateService.updateSignature(new SettingsUpdate(true, version), admin));
        InventoryAssignmentDTO clarinet = assign("INV-1", "Clarinetto", admin);
        long sheetId = tx(() -> loanSheetService.issue(memberIndex, new IssueRequest(
            List.of(clarinet.id()), LocalDate.now(), "Roma", InventoryLoanHolderRole.MEMBER, null, null, null, InventoryLoanSignatureMode.ELECTRONIC, adminKey
        ), admin)).summary().id();
        Act issue = single(tx(() -> signatureService.ownActs(member)));
        tx(() -> signatureService.submitHolderData(issue.id(), holderData("RSSMRA85T10A562S"), member));
        Act withData = tx(() -> signatureService.ownAct(issue.id(), member));

        assertThatThrownBy(() -> tx(() -> signatureService.changeAssociationSigner(issue.id(), new AssociationSignerChange(otherAdmin, withData.entityVersion() - 1), admin)))
            .hasFieldOrPropertyWithValue("errorKey", "signature.act.versionConflict");
        Act changed = tx(() -> signatureService.changeAssociationSigner(issue.id(), new AssociationSignerChange(otherAdmin, withData.entityVersion()), admin));
        assertThat(changed.status()).isEqualTo(SignatureActStatus.AWAITING_DATA);
        assertThat(changed.documents()).isEmpty();
        assertThat(changed.signers()).filteredOn(signer -> signer.role() == com.fundaro.zodiac.taurus.domain.signature.SignerRole.ASSOCIATION)
            .extracting(ActSigner::displayName).containsExactly("Luca Verdi");
        assertThat(tx(() -> loanSheetService.detail(sheetId, admin)).associationSignatoryName()).isEqualTo("Luca Verdi");

        Challenge declaration = inTenant(() -> challengeService.create(new ChallengeRequest(
            SignaturePurpose.PAPER_DECLARATION,
            SignatureMethod.EMAIL_OTP,
            List.of(ChallengeItem.paper(issue.id(), "Firmato dal socio in sede"))
        ), http(), admin));
        assertThat(declaration.declarations()).singleElement().asString().contains("firmato su carta").contains("Firmato dal socio in sede");
        assertThatThrownBy(() -> inTenant(() -> challengeService.create(new ChallengeRequest(
            SignaturePurpose.PAPER_DECLARATION,
            SignatureMethod.EMAIL_OTP,
            List.of(ChallengeItem.paper(issue.id(), "Tentativo del socio"))
        ), http(), member))).hasFieldOrPropertyWithValue("errorKey", "signature.act.notFound");
        confirm(declaration, admin);

        Act paper = tx(() -> signatureService.sheetActs(sheetId, admin)).get(0);
        assertThat(paper.status()).isEqualTo(SignatureActStatus.SIGNED_ON_PAPER);
        assertThat(paper.paperNote()).isEqualTo("Firmato dal socio in sede");
        assertThat(paper.signers()).extracting(ActSigner::status).containsOnly(com.fundaro.zodiac.taurus.domain.signature.SignerStatus.SIGNED_ON_PAPER);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT method || '|' || coalesce(cast(signer_id AS varchar), 'none') FROM " + schema() + ".signature_evidence", String.class
        )).isEqualTo("PAPER_DECLARATION|none");
        assertThat(tx(() -> evidenceService.verify()).valid()).isTrue();
    }

    @Test
    void aParentSignsFromTheLinkAndTheMinorAcknowledgesTheSheet() {
        JwtAuthenticationToken admin = token(adminKey);
        JwtAuthenticationToken minor = token(memberKey);
        transactionExecutor.execute(TenantSchemaNameResolver.DEFAULT_SCHEMA, () -> jdbcTemplate.update("UPDATE public.tenant SET inventory_enabled = true WHERE id = ?", tenantId));
        long version = tx(() -> templateService.overview(admin).settingsVersion());
        tx(() -> templateService.updateSignature(new SettingsUpdate(true, version), admin));
        InventoryAssignmentDTO clarinet = assign("INV-1", "Clarinetto", admin);

        assertThatThrownBy(() -> tx(() -> loanSheetService.issue(memberIndex, guardianIssue(clarinet.id(), null), admin)))
            .hasFieldOrPropertyWithValue("errorKey", "inventory.loanSheet.holderEmailRequired");
        long sheetId = tx(() -> loanSheetService.issue(memberIndex, guardianIssue(clarinet.id(), "genitore@example.org"), admin)).summary().id();
        assertThat(tx(() -> loanSheetService.detail(sheetId, admin)).summary().signatureMode()).isEqualTo(InventoryLoanSignatureMode.ELECTRONIC);
        SimpleMailMessage linkMail = lastMailTo("genitore@example.org");
        assertThat(linkMail.getSubject()).isEqualTo("Firma richiesta – Banda musicale di prova");
        assertThat(linkMail.getText()).contains("Socio minorenne: Mario Rossi").contains("/firma/");
        String link = linkToken(linkMail);

        LinkAct opened = linkResolver.within(link, "192.0.2.10", signatureService::linkAct);
        assertThat(opened.tenantName()).isEqualTo("Banda musicale di prova");
        assertThat(opened.minorName()).isEqualTo("Mario Rossi");
        assertThat(opened.maskedEmail()).isEqualTo("g***@example.org");
        assertThat(opened.act().holderRole()).isEqualTo(InventoryLoanHolderRole.GUARDIAN);
        assertThat(opened.act().dataRequired()).isTrue();
        assertThat(opened.act().signers()).extracting(ActSigner::userId).containsOnlyNulls();
        Act minorView = single(tx(() -> signatureService.ownActs(minor)));
        assertThat(minorView.pendingPurposes()).isEmpty();
        assertThat(minorView.dataRequired()).isFalse();

        linkResolver.within(link, "192.0.2.10", session -> signatureService.submitLinkHolderData(session, guardianData()));
        LinkAct withData = linkResolver.within(link, "192.0.2.10", signatureService::linkAct);
        assertThat(withData.act().status()).isEqualTo(SignatureActStatus.AWAITING_SIGNATURES);
        assertThat(withData.act().pendingPurposes()).containsExactly(SignaturePurpose.ACCEPTANCE, SignaturePurpose.CLAUSE_APPROVAL);
        assertThat(withData.act().signers()).filteredOn(signer -> signer.role() == com.fundaro.zodiac.taurus.domain.signature.SignerRole.HOLDER)
            .extracting(ActSigner::displayName).containsExactly("Giulia Bianchi");
        assertThat(jdbcTemplate.queryForObject("SELECT holder_name FROM " + schema() + ".inventory_loan_sheet WHERE id = ?", String.class, sheetId))
            .isEqualTo("Giulia Bianchi");
        String mainHash = hash(withData.act(), SignatureDocumentRole.MAIN);
        ActFile frozen = linkResolver.within(link, "192.0.2.10", session -> signatureService.linkDocument(session, SignatureDocumentRole.MAIN));
        assertThat(SignatureSupport.sha256(frozen.bytes())).isEqualTo(mainHash);
        assertThat(withData.act().cieSignAvailable()).isFalse();
        assertThatThrownBy(() -> linkResolver.within(link, "192.0.2.10", session ->
            cieSignService.signForLink(session, SignatureDocumentRole.MAIN, frozen.bytes(), http())))
            .hasFieldOrPropertyWithValue("errorKey", "signature.cieSign.notAvailable");

        Challenge acceptance = linkResolver.within(link, "192.0.2.10", session ->
            challengeService.createForLink(session, new LinkChallengeRequest(SignaturePurpose.ACCEPTANCE, mainHash), http()));
        assertThat(acceptance.maskedEmail()).isEqualTo("g***@example.org");
        String code = sentCode();
        assertThat(lastMailTo("genitore@example.org").getText()).contains(code);
        linkResolver.within(link, "192.0.2.10", session -> challengeService.confirmForLink(session, acceptance.id(), new ConfirmRequest(code), http()));
        Challenge clauses = linkResolver.within(link, "192.0.2.10", session ->
            challengeService.createForLink(session, new LinkChallengeRequest(SignaturePurpose.CLAUSE_APPROVAL, hash(withData.act(), SignatureDocumentRole.CLAUSES)), http()));
        linkResolver.within(link, "192.0.2.10", session -> challengeService.confirmForLink(session, clauses.id(), new ConfirmRequest(sentCode()), http()));

        Act forMinor = tx(() -> signatureService.ownAct(minorView.id(), minor));
        assertThat(forMinor.pendingPurposes()).containsExactly(SignaturePurpose.MINOR_ACKNOWLEDGMENT);
        Challenge acknowledgment = challenge(SignaturePurpose.MINOR_ACKNOWLEDGMENT, forMinor, SignatureDocumentRole.MAIN, minor);
        assertThat(acknowledgment.declarations()).singleElement().asString().contains("Ho preso visione");
        confirm(acknowledgment, minor);

        Act awaitingAssociation = tx(() -> signatureService.ownAct(minorView.id(), admin));
        assertThat(awaitingAssociation.signers()).filteredOn(signer -> signer.userId() == null)
            .singleElement()
            .satisfies(parent -> {
                assertThat(parent.maskedEmail()).isEqualTo("g***@example.org");
                assertThat(parent.link().active()).isTrue();
                assertThat(parent.link().sentAt()).isNotNull();
            });
        confirm(challenge(SignaturePurpose.ASSOCIATION_SIGNATURE, awaitingAssociation, SignatureDocumentRole.MAIN, admin), admin);

        assertThatThrownBy(() -> linkResolver.within(link, "192.0.2.10", signatureService::linkAct)).isInstanceOf(SignatureLinkUnavailableException.class);
        SimpleMailMessage signedMail = lastMailTo("genitore@example.org");
        assertThat(signedMail.getSubject()).isEqualTo("Atto firmato – Banda musicale di prova");
        String download = linkToken(signedMail);
        LinkAct signed = linkResolver.within(download, "192.0.2.10", signatureService::linkAct);
        assertThat(signed.act().status()).isEqualTo(SignatureActStatus.SIGNED);
        assertThat(signed.act().attestationAvailable()).isTrue();
        assertThat(linkResolver.within(download, "192.0.2.10", signatureService::linkAttestation).bytes()).isNotEmpty();
        assertThat(jdbcTemplate.queryForList(
            "SELECT purpose || '|' || coalesce(signer_keycloak_id, 'none') || '|' || coalesce(destination_email, 'none') || '|' || coalesce(session_id, 'none') FROM "
                + schema() + ".signature_evidence ORDER BY sequence",
            String.class
        )).satisfiesExactly(
            row -> assertThat(row).startsWith("ACCEPTANCE|none|genitore@example.org|link:"),
            row -> assertThat(row).startsWith("CLAUSE_APPROVAL|none|genitore@example.org|link:"),
            row -> assertThat(row).startsWith("MINOR_ACKNOWLEDGMENT|" + memberKey + "|"),
            row -> assertThat(row).startsWith("ASSOCIATION_SIGNATURE|" + adminKey + "|")
        );
        assertThat(tx(() -> evidenceService.verify())).isEqualTo(new ChainVerification(true, 4, null, null));

        inTenant(() -> linkAdministration.resend(minorView.id(), parentId(awaitingAssociation), admin));
        String again = linkToken(lastMailTo("genitore@example.org"));
        assertThat(again).isNotEqualTo(download);
        assertThatThrownBy(() -> linkResolver.within(download, "192.0.2.10", signatureService::linkAct)).isInstanceOf(SignatureLinkUnavailableException.class);
        assertThat(linkResolver.within(again, "192.0.2.10", signatureService::linkAct).act().id()).isEqualTo(minorView.id());

        assign("INV-2", "Leggio", admin);
        String variationLink = linkToken(lastMailTo("genitore@example.org"));
        LinkAct variation = linkResolver.within(variationLink, "192.0.2.10", signatureService::linkAct);
        assertThat(variation.act().type()).isEqualTo(SignatureActType.LOAN_SHEET_VARIATION);
        assertThat(variation.act().pendingPurposes()).containsExactly(SignaturePurpose.ACCEPTANCE);

        transactionExecutor.execute(TenantSchemaNameResolver.DEFAULT_SCHEMA, () -> jdbcTemplate.update("UPDATE public.tenant SET inventory_enabled = false WHERE id = ?", tenantId));
        assertThatThrownBy(() -> linkResolver.within(variationLink, "192.0.2.10", signatureService::linkAct)).isInstanceOf(SignatureLinkUnavailableException.class);
        transactionExecutor.execute(TenantSchemaNameResolver.DEFAULT_SCHEMA, () -> jdbcTemplate.update("UPDATE public.tenant SET inventory_enabled = true WHERE id = ?", tenantId));

        tx(() -> loanSheetService.cancel(sheetId, new com.fundaro.zodiac.taurus.service.dto.inventory.InventoryLoanSheetDtos.CancelRequest("Emesso per errore"), admin));
        assertThatThrownBy(() -> linkResolver.within(variationLink, "192.0.2.10", signatureService::linkAct)).isInstanceOf(SignatureLinkUnavailableException.class);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + schema() + ".signature_act_signer s JOIN " + schema() + ".signature_act a ON a.id = s.act_id"
                + " WHERE a.status = 'CANCELLED' AND s.email IS NOT NULL",
            Long.class
        )).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT holder_email FROM " + schema() + ".inventory_loan_sheet WHERE id = ?", String.class, sheetId)).isNull();
    }

    @Test
    void theHolderSignsTheSheetWithCieSignAndTheClausesWithACode() throws IOException {
        JwtAuthenticationToken admin = token(adminKey);
        JwtAuthenticationToken member = token(memberKey);
        long version = tx(() -> templateService.overview(admin).settingsVersion());
        tx(() -> templateService.updateSignature(new SettingsUpdate(true, version), admin));
        InventoryAssignmentDTO clarinet = assign("INV-1", "Clarinetto", admin);
        tx(() -> loanSheetService.issue(memberIndex, new IssueRequest(
            List.of(clarinet.id()), LocalDate.now(), "Roma", InventoryLoanHolderRole.MEMBER, null, null, null, InventoryLoanSignatureMode.ELECTRONIC, adminKey
        ), admin));
        Act issue = single(tx(() -> signatureService.ownActs(member)));
        assertThat(issue.cieSignAvailable()).isFalse();
        Act withData = tx(() -> signatureService.submitHolderData(issue.id(), holderData(CieSignTestSupport.TAX_CODE), member));
        assertThat(withData.cieSignAvailable()).isTrue();
        assertThat(tx(() -> signatureService.ownAct(issue.id(), admin)).cieSignAvailable()).isFalse();

        byte[] frozenMain = tx(() -> signatureService.document(issue.id(), SignatureDocumentRole.MAIN, false, member)).bytes();
        CieSignTestSupport.Issued mario = cie.holder("MARIO", "ROSSI", CieSignTestSupport.TAX_CODE, null);
        byte[] signedMain = CieSignTestSupport.sign(frozenMain, mario, cie.chain(mario));
        assertThatThrownBy(() -> inTenant(() -> cieSignService.sign(issue.id(), SignatureDocumentRole.MAIN, signedMain, http(), admin)))
            .hasFieldOrPropertyWithValue("errorKey", "signature.act.notFound");
        ActOutcome accepted = inTenant(() -> cieSignService.sign(issue.id(), SignatureDocumentRole.MAIN, signedMain, http(), member));
        assertThat(accepted.status()).isEqualTo(SignatureActStatus.AWAITING_SIGNATURES);
        assertThatThrownBy(() -> inTenant(() -> cieSignService.sign(issue.id(), SignatureDocumentRole.MAIN, signedMain, http(), member)))
            .hasFieldOrPropertyWithValue("errorKey", "signature.act.alreadySigned");

        byte[] frozenClauses = tx(() -> signatureService.document(issue.id(), SignatureDocumentRole.CLAUSES, false, member)).bytes();
        CieSignTestSupport.Issued luigi = cie.holder("LUIGI", "VERDI", CieSignTestSupport.OTHER_TAX_CODE, null);
        byte[] signedByAnother = CieSignTestSupport.sign(frozenClauses, luigi, cie.chain(luigi));
        assertThatThrownBy(() -> inTenant(() -> cieSignService.sign(issue.id(), SignatureDocumentRole.CLAUSES, signedByAnother, http(), member)))
            .hasFieldOrPropertyWithValue("errorKey", "signature.cieSign.signerMismatch");
        assertThatThrownBy(() -> inTenant(() -> cieSignService.sign(issue.id(), SignatureDocumentRole.CLAUSES, signedMain, http(), member)))
            .hasFieldOrPropertyWithValue("errorKey", "signature.cieSign.documentMismatch");
        assertThat(jdbcTemplate.queryForList(
            "SELECT role || '|' || (signed_media_asset_id IS NOT NULL) FROM " + schema() + ".signature_document WHERE act_id = ? ORDER BY id",
            String.class,
            issue.id()
        )).containsExactly("MAIN|true", "CLAUSES|false");

        Act afterCieSign = tx(() -> signatureService.ownAct(issue.id(), member));
        assertThat(afterCieSign.pendingPurposes()).containsExactly(SignaturePurpose.CLAUSE_APPROVAL);
        confirm(challenge(SignaturePurpose.CLAUSE_APPROVAL, afterCieSign, SignatureDocumentRole.CLAUSES, member), member);
        Act awaitingAssociation = tx(() -> signatureService.ownAct(issue.id(), admin));
        confirm(challenge(SignaturePurpose.ASSOCIATION_SIGNATURE, awaitingAssociation, SignatureDocumentRole.MAIN, admin), admin);

        Act signed = tx(() -> signatureService.ownAct(issue.id(), member));
        assertThat(signed.status()).isEqualTo(SignatureActStatus.SIGNED);
        byte[] sealed = tx(() -> signatureService.document(issue.id(), SignatureDocumentRole.MAIN, false, member)).bytes();
        // The seal is appended to the file signed with CieSign, whose signature keeps covering the same bytes.
        assertThat(java.util.Arrays.equals(sealed, 0, signedMain.length, signedMain, 0, signedMain.length)).isTrue();
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(sealed)) {
            assertThat(document.getSignatureDictionaries()).hasSize(2);
            int[] range = document.getSignatureDictionaries().get(0).getByteRange();
            assertThat((long) range[2] + range[3]).isEqualTo(signedMain.length);
        }
        List<Evidence> evidence = tx(() -> signatureService.evidence(issue.id(), admin));
        assertThat(evidence.get(0)).satisfies(cieSign -> {
            assertThat(cieSign.purpose()).isEqualTo(SignaturePurpose.ACCEPTANCE);
            assertThat(cieSign.method()).isEqualTo(com.fundaro.zodiac.taurus.domain.signature.SignatureEvidenceMethod.CIESIGN);
            assertThat(cieSign.certificateTaxCode()).isEqualTo(CieSignTestSupport.TAX_CODE);
            assertThat(cieSign.certificateSubject()).contains("GIVENNAME=MARIO");
            assertThat(cieSign.revocationCheck()).isEqualTo(CieRevocationChecker.UNAVAILABLE);
            assertThat(cieSign.maskedEmail()).isNull();
        });
        assertThat(evidence).extracting(Evidence::method).containsExactly(
            com.fundaro.zodiac.taurus.domain.signature.SignatureEvidenceMethod.CIESIGN,
            com.fundaro.zodiac.taurus.domain.signature.SignatureEvidenceMethod.EMAIL_OTP,
            com.fundaro.zodiac.taurus.domain.signature.SignatureEvidenceMethod.EMAIL_OTP
        );
        assertThat(tx(() -> evidenceService.verify())).isEqualTo(new ChainVerification(true, 3, null, null));
    }

    private IssueRequest guardianIssue(long assignmentId, String holderEmail) {
        return new IssueRequest(
            List.of(assignmentId),
            LocalDate.now(),
            "Roma",
            InventoryLoanHolderRole.GUARDIAN,
            null,
            null,
            null,
            InventoryLoanSignatureMode.ELECTRONIC,
            adminKey,
            holderEmail
        );
    }

    private static HolderDataRequest guardianData() {
        return new HolderDataRequest("Napoli", "Via Toledo 1", "80100", "Napoli (NA)", null, true, "Patente NA0000000", "MCTC Napoli",
            LocalDate.now().minusYears(2), null, "Roma", "Giulia", "Bianchi", LocalDate.of(1980, 3, 4));
    }

    private static String hash(Act act, SignatureDocumentRole role) {
        return act.documents().stream().filter(document -> document.role() == role).findFirst().orElseThrow().frozenHash();
    }

    private static long parentId(Act act) {
        return act.signers().stream().filter(signer -> signer.userId() == null).findFirst().orElseThrow().id();
    }

    @Test
    void refusesTheDataOfTheHolderUntilTheProfileHasTheDateOfBirth() {
        JwtAuthenticationToken admin = token(adminKey);
        JwtAuthenticationToken member = token(memberKey);
        long version = tx(() -> templateService.overview(admin).settingsVersion());
        tx(() -> templateService.updateSignature(new SettingsUpdate(true, version), admin));
        InventoryAssignmentDTO clarinet = assign("INV-1", "Clarinetto", admin);
        jdbcTemplate.update("UPDATE " + schema() + ".app_user SET birth_date = NULL WHERE keycloak_id = ?", memberKey);
        long sheetId = tx(() -> loanSheetService.issue(memberIndex, new IssueRequest(
            List.of(clarinet.id()), LocalDate.now(), "Roma", InventoryLoanHolderRole.MEMBER, null, null, null, InventoryLoanSignatureMode.ELECTRONIC, adminKey
        ), admin)).summary().id();

        Act issue = single(tx(() -> signatureService.ownActs(member)));
        assertThat(issue.dataRequired()).isTrue();
        assertThat(issue.memberBirthDate()).isNull();
        assertThatThrownBy(() -> tx(() -> signatureService.submitHolderData(issue.id(), holderData("RSSMRA85T10A562S"), member)))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("signature.holderData.birthDateMissing");
                assertThat(exception.getArgs().values()).containsEntry("holderRole", "MEMBER");
            });
        assertThat(tx(() -> signatureService.ownAct(issue.id(), member)).status()).isEqualTo(SignatureActStatus.AWAITING_DATA);

        // The member adds the date of birth in the profile: it goes in the frozen documents.
        LocalDate birthDate = LocalDate.of(1990, 3, 4);
        jdbcTemplate.update("UPDATE " + schema() + ".app_user SET birth_date = ? WHERE keycloak_id = ?", java.sql.Date.valueOf(birthDate), memberKey);
        assertThat(single(tx(() -> signatureService.ownActs(member))).memberBirthDate()).isEqualTo(birthDate);
        Act withData = tx(() -> signatureService.submitHolderData(issue.id(), holderData("RSSMRA85T10A562S"), member));
        assertThat(withData.status()).isEqualTo(SignatureActStatus.AWAITING_SIGNATURES);
        assertThat(withData.memberBirthDate()).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT member_birth_date FROM " + schema() + ".inventory_loan_sheet WHERE id = ?", LocalDate.class, sheetId))
            .isEqualTo(birthDate);
    }

    @Test
    void theHolderSignsTheSheetOnlyAfterAcknowledgingItsItems() {
        JwtAuthenticationToken admin = token(adminKey);
        JwtAuthenticationToken member = token(memberKey);
        long version = tx(() -> templateService.overview(admin).settingsVersion());
        tx(() -> templateService.updateSignature(new SettingsUpdate(true, version), admin));
        InventoryAssignmentDTO clarinet = assign("INV-1", "Clarinetto", admin);
        InventoryAssignmentDTO uniform = assignToAcknowledge("INV-2", "Divisa", admin);
        long sheetId = tx(() -> loanSheetService.issue(memberIndex, new IssueRequest(
            List.of(clarinet.id(), uniform.id()), LocalDate.now(), "Roma", InventoryLoanHolderRole.MEMBER, null, null, null, InventoryLoanSignatureMode.ELECTRONIC, adminKey
        ), admin)).summary().id();

        Act issue = single(tx(() -> signatureService.ownActs(member)));
        assertThat(issue.pendingAcknowledgments()).isEqualTo(1);
        assertThat(tx(() -> signatureService.ownAct(issue.id(), admin)).pendingAcknowledgments()).isZero();
        assertThatThrownBy(() -> tx(() -> signatureService.submitHolderData(issue.id(), holderData("RSSMRA85T10A562S"), member)))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("signature.act.acknowledgmentsPending");
                assertThat(exception.getArgs().values()).containsEntry("count", 1L);
            });
        assertThat(tx(() -> signatureService.ownAct(issue.id(), member)).status()).isEqualTo(SignatureActStatus.AWAITING_DATA);

        // The member acknowledges the uniform with a code: the holder can enter the data.
        Challenge acknowledgment = inTenant(() -> challengeService.create(new ChallengeRequest(
            SignaturePurpose.ASSIGNMENT_DECISION,
            SignatureMethod.EMAIL_OTP,
            List.of(new ChallengeItem(uniform.id(), uniform.revisionHash(), InventoryDecisionType.ACCEPTED, null))
        ), http(), member));
        confirm(acknowledgment, member);
        Act withData = tx(() -> signatureService.submitHolderData(issue.id(), holderData("RSSMRA85T10A562S"), member));
        assertThat(withData.pendingAcknowledgments()).isZero();

        // A further item joins the sheet before the signature: the holder signs once the member has acknowledged it.
        InventoryAssignmentDTO stand = assignToAcknowledge("INV-3", "Leggio", admin);
        jdbcTemplate.update("UPDATE " + schema() + ".inventory_assignment SET loan_sheet_id = ? WHERE id = ?", sheetId, stand.id());
        assertThat(tx(() -> signatureService.ownAct(issue.id(), member)).pendingAcknowledgments()).isEqualTo(1);
        assertThatThrownBy(() -> challenge(SignaturePurpose.ACCEPTANCE, withData, SignatureDocumentRole.MAIN, member))
            .hasFieldOrPropertyWithValue("errorKey", "signature.act.acknowledgmentsPending");
        acknowledgedWithoutSignature(stand);
        Act ready = tx(() -> signatureService.ownAct(issue.id(), member));
        assertThat(ready.pendingAcknowledgments()).isZero();
        confirm(challenge(SignaturePurpose.ACCEPTANCE, ready, SignatureDocumentRole.MAIN, member), member);
        assertThat(tx(() -> signatureService.ownAct(issue.id(), member)).pendingPurposes()).containsExactly(SignaturePurpose.CLAUSE_APPROVAL);
    }

    private SimpleMailMessage lastMailTo(String address) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        List<SimpleMailMessage> sent = captor.getAllValues();
        for (int index = sent.size() - 1; index >= 0; index--) {
            String[] to = sent.get(index).getTo();
            if (to != null && List.of(to).contains(address)) return sent.get(index);
        }
        throw new AssertionError("No e-mail to " + address);
    }

    private static String linkToken(SimpleMailMessage mail) {
        Matcher matcher = LINK.matcher(mail.getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private Challenge challenge(SignaturePurpose purpose, Act act, SignatureDocumentRole role, JwtAuthenticationToken signer) {
        return inTenant(() -> challengeService.create(request(purpose, act, role), http(), signer));
    }

    private static ChallengeRequest request(SignaturePurpose purpose, Act act, SignatureDocumentRole role) {
        String hash = act.documents().stream().filter(document -> document.role() == role).findFirst().orElseThrow().frozenHash();
        return new ChallengeRequest(purpose, SignatureMethod.EMAIL_OTP, List.of(ChallengeItem.document(act.id(), role, hash)));
    }

    private Confirmation confirm(Challenge challenge, JwtAuthenticationToken signer) {
        return inTenant(() -> challengeService.confirm(challenge.id(), new ConfirmRequest(sentCode()), http(), signer));
    }

    private void completeReturn(InventoryAssignmentDTO assignment, JwtAuthenticationToken admin, JwtAuthenticationToken member) {
        InventoryReturnDTO requested = tx(() -> inventoryService.requestReturn(assignment.id(), new InventoryReturnRequest(1, null, null), true, member));
        tx(() -> inventoryService.completeReturn(requested.id(), new InventoryReturnRequest(1, InventoryCondition.GOOD, null), admin));
    }

    private static HolderDataRequest holderData(String taxCode) {
        return new HolderDataRequest("Roma", "Via Garibaldi 5", "00100", "Roma (RM)", taxCode, false, "Carta d'identità CA00000AA", "Comune di Roma",
            LocalDate.now().minusYears(2), null, null);
    }

    private static Act single(List<Act> acts) {
        assertThat(acts).hasSize(1);
        return acts.get(0);
    }

    private static List<String> zipEntries(byte[] zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) names.add(entry.getName());
        }
        return names;
    }

    /**
     * Assigns an item to the member, who has already acknowledged it: the holder signs the sheet only after the
     * acknowledgments.
     */
    private InventoryAssignmentDTO assign(String number, String name, JwtAuthenticationToken admin) {
        InventoryAssignmentDTO assignment = assignToAcknowledge(number, name, admin);
        acknowledgedWithoutSignature(assignment);
        return assignment;
    }

    private InventoryAssignmentDTO assignToAcknowledge(String number, String name, JwtAuthenticationToken admin) {
        long itemId = tx(() -> inventoryService.createItem(new InventoryItemRequest(
            number, name, null, 1, BigDecimal.TEN, "EUR", InventoryCondition.GOOD, null, null, null, InventoryLoanType.PRECARIOUS
        ), admin).id());
        return tx(() -> inventoryService.assign(itemId, new InventoryAssignmentRequest(memberIndex, 0, 1, null, null, null), admin));
    }

    /**
     * An acknowledgment recorded before the electronic signature, without evidence: it keeps the evidence chain of the
     * tests on the acts of the sheet.
     */
    private void acknowledgedWithoutSignature(InventoryAssignmentDTO assignment) {
        jdbcTemplate.update(
            "INSERT INTO " + schema() + ".inventory_assignment_decision "
                + "(deleted, insert_date, insert_by, edit_date, edit_by, revision_id, decision, decided_at, decided_by, authenticated_hash) "
                + "SELECT false, now(), ?, now(), ?, r.id, 'ACCEPTED', now(), ?, ? FROM " + schema() + ".inventory_assignment_revision r "
                + "JOIN " + schema() + ".inventory_assignment a ON a.id = r.assignment_id AND r.revision_number = a.current_revision WHERE a.id = ?",
            memberKey,
            memberKey,
            memberKey,
            "0".repeat(64),
            assignment.id()
        );
    }

    private String sentCode() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        Matcher matcher = CODE.matcher(captor.getValue().getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private long createUser(String keycloakId, String name, String lastName, String email, String role) {
        Long identityId = transactionExecutor.execute(
            TenantSchemaNameResolver.DEFAULT_SCHEMA,
            () -> jdbcTemplate.queryForObject(
                "INSERT INTO public.user_identity (keycloak_id, created_at) VALUES (?, CURRENT_TIMESTAMP) RETURNING id",
                Long.class,
                keycloakId
            )
        );
        identityIds.add(identityId);
        Long userId = jdbcTemplate.queryForObject(
            "INSERT INTO " + schema() + ".app_user (user_identity_id, keycloak_id, name, last_name, email, birth_date) VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
            Long.class,
            identityId,
            keycloakId,
            name,
            lastName,
            email,
            java.sql.Date.valueOf(LocalDate.now().minusYears(30))
        );
        jdbcTemplate.update("INSERT INTO " + schema() + ".app_user_role (user_id, role) VALUES (?, ?)", userId, role);
        return userId;
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
