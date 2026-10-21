package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignment;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentDecision;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentRevision;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryItem;
import com.fundaro.zodiac.taurus.domain.signature.*;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentDecisionRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActSignerRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureDocumentRepository;
import com.fundaro.zodiac.taurus.service.dto.inventory.InventoryDecisionRequest;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ActOutcome;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ChallengeItem;
import com.fundaro.zodiac.taurus.service.impl.InventoryService;
import com.fundaro.zodiac.taurus.service.impl.InventoryService.DecisionCheck;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssignmentDecisionSignatureHandlerTest {

    private static final String HASH_5 = "5".repeat(64);
    private static final String HASH_6 = "6".repeat(64);

    @Mock
    InventoryService inventoryService;
    @Mock
    InventoryAssignmentDecisionRepository decisionRepository;
    @Mock
    SignatureActRepository actRepository;
    @Mock
    SignatureDocumentRepository documentRepository;
    @Mock
    SignatureActSignerRepository signerRepository;
    @Mock
    SignatureEvidenceService evidenceService;

    private final JwtAuthenticationToken token = token();
    private final Signer signer = new Signer("user-1", null, "user-1", "Mario Rossi", "mario.rossi@example.org", "session-1", "192.0.2.10", "Mozilla/5.0", "tenant-a", token);
    private final List<SignatureAct> acts = new ArrayList<>();
    private AssignmentDecisionSignatureHandler handler;

    @BeforeEach
    void setUp() {
        when(inventoryService.electronicSignatureEnabled()).thenReturn(true);
        when(inventoryService.checkDecision(eq(5L), any(), eq("user-1"), anyBoolean()))
            .thenAnswer(invocation -> check(5L, "Clarinetto", "INV-5", HASH_5, invocation.getArgument(1)));
        when(inventoryService.checkDecision(eq(6L), any(), eq("user-1"), anyBoolean()))
            .thenAnswer(invocation -> check(6L, "Divisa", "INV-6", HASH_6, invocation.getArgument(1)));
        when(actRepository.save(any(SignatureAct.class))).thenAnswer(invocation -> {
            SignatureAct act = invocation.getArgument(0);
            act.setId(100L + acts.size());
            acts.add(act);
            return act;
        });
        when(documentRepository.save(any(SignatureDocument.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 200L));
        when(signerRepository.save(any(SignatureActSigner.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 300L));
        when(evidenceService.append(any(), anyString())).thenAnswer(invocation -> {
            SignatureEvidence evidence = new SignatureEvidence();
            evidence.setId(400L);
            evidence.setSequence(1);
            return evidence;
        });
        ApplicationProperties properties = new ApplicationProperties();
        properties.getSignature().setMaxBatchItems(2);
        handler = new AssignmentDecisionSignatureHandler(
            inventoryService, decisionRepository, actRepository, documentRepository, signerRepository, evidenceService,
            new SignatureMetrics(new SimpleMeterRegistry()), properties
        );
    }

    @Test
    void refusesWhenTheTenantDoesNotUseTheElectronicSignature() {
        when(inventoryService.electronicSignatureEnabled()).thenReturn(false);

        assertError(() -> handler.prepare(List.of(item(5L, HASH_5, InventoryDecisionType.ACCEPTED, null)), signer), "signature.disabled");
    }

    @Test
    void refusesEmptyIncompleteDuplicateOrTooManyItems() {
        assertError(() -> handler.prepare(List.of(), signer), "signature.challenge.invalid");
        assertError(() -> handler.prepare(List.of(item(5L, null, InventoryDecisionType.ACCEPTED, null)), signer), "signature.challenge.invalid");
        assertError(() -> handler.prepare(List.of(
            item(5L, HASH_5, InventoryDecisionType.ACCEPTED, null),
            item(5L, HASH_5, InventoryDecisionType.ACCEPTED, null)
        ), signer), "signature.challenge.invalid");
        assertError(() -> handler.prepare(Collections.nCopies(3, item(5L, HASH_5, InventoryDecisionType.ACCEPTED, null)), signer), "signature.challenge.invalid");
    }

    @Test
    void describesWhatTheCodeConfirms() {
        SignaturePurposeHandler.Prepared single = handler.prepare(List.of(item(5L, HASH_5, InventoryDecisionType.ACCEPTED, null)), signer);

        assertThat(single.operation()).isEqualTo("Presa visione dell'assegnazione di «Clarinetto»");
        assertThat(single.reference()).isEqualTo("55555555");
        assertThat(single.declarations()).containsExactly(
            "Ho preso visione dei dati e delle fotografie dell'assegnazione di «Clarinetto» (numero inventariale INV-5, revisione 2) e li accetto."
        );

        SignaturePurposeHandler.Prepared batch = handler.prepare(List.of(
            item(5L, HASH_5, InventoryDecisionType.ACCEPTED, null),
            item(6L, HASH_6, InventoryDecisionType.REJECTED, "  Manca la custodia ")
        ), signer);

        assertThat(batch.operation()).isEqualTo("Presa visione di 2 assegnazioni");
        assertThat(batch.reference()).isEqualTo(SignatureSupport.sha256(batch.itemsJson()).substring(0, 8));
        assertThat(batch.declarations().get(1)).isEqualTo(
            "Ho preso visione dei dati e delle fotografie dell'assegnazione di «Divisa» (numero inventariale INV-6, revisione 2) e li rifiuto per il seguente motivo: Manca la custodia"
        );
        assertThat(handler.describe(batch.itemsJson())).isEqualTo(batch);
        verify(inventoryService, never()).checkDecision(anyLong(), any(), anyString(), eq(true));
    }

    @Test
    void recordsEveryDecisionWithItsActDocumentSignerAndEvidence() {
        SignaturePurposeHandler.Prepared prepared = handler.prepare(List.of(
            item(5L, HASH_5, InventoryDecisionType.ACCEPTED, null),
            item(6L, HASH_6, InventoryDecisionType.REJECTED, "Manca la custodia")
        ), signer);
        SignatureChallenge challenge = new SignatureChallenge();
        challenge.setId(77L);
        challenge.setMethod(SignatureMethod.EMAIL_OTP);
        challenge.setDestinationEmail("mario.rossi@example.org");
        challenge.setItemsJson(prepared.itemsJson());
        List<InventoryAssignmentDecision> decisions = new ArrayList<>();
        doAnswer(invocation -> {
            long assignmentId = invocation.getArgument(0);
            InventoryDecisionRequest request = invocation.getArgument(1);
            InventoryService.SignedDecisionRecorder recorder = invocation.getArgument(3);
            InventoryAssignmentDecision decision = new InventoryAssignmentDecision();
            decision.setId(assignmentId * 10);
            decisions.add(decision);
            recorder.record(check(assignmentId, "x", "x", assignmentId == 5L ? HASH_5 : HASH_6, request), decision);
            return null;
        }).when(inventoryService).decideSigned(anyLong(), any(), eq(token), any());

        List<ActOutcome> outcomes = handler.confirm(challenge, signer);

        assertThat(outcomes).extracting(ActOutcome::assignmentId).containsExactly(5L, 6L);
        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.type()).isEqualTo(SignatureActType.ASSIGNMENT_ACKNOWLEDGMENT);
            assertThat(outcome.status()).isEqualTo(SignatureActStatus.SIGNED);
        });
        assertThat(acts).extracting(SignatureAct::getDecisionId).containsExactly(50L, 60L);
        assertThat(decisions).allSatisfy(decision -> assertThat(decision.getSignatureEvidenceId()).isEqualTo(400L));

        ArgumentCaptor<InventoryDecisionRequest> requests = ArgumentCaptor.forClass(InventoryDecisionRequest.class);
        verify(inventoryService, times(2)).decideSigned(anyLong(), requests.capture(), eq(token), any());
        assertThat(requests.getAllValues().get(1)).isEqualTo(new InventoryDecisionRequest(InventoryDecisionType.REJECTED, "Manca la custodia", HASH_6));

        ArgumentCaptor<SignatureDocument> documents = ArgumentCaptor.forClass(SignatureDocument.class);
        verify(documentRepository, times(2)).save(documents.capture());
        assertThat(documents.getAllValues()).extracting(SignatureDocument::getRole).containsOnly(SignatureDocumentRole.SNAPSHOT);
        assertThat(documents.getAllValues()).extracting(SignatureDocument::getFrozenHash).containsExactly(HASH_5, HASH_6);

        ArgumentCaptor<SignatureActSigner> signers = ArgumentCaptor.forClass(SignatureActSigner.class);
        verify(signerRepository, times(2)).save(signers.capture());
        assertThat(signers.getAllValues()).allSatisfy(value -> {
            assertThat(value.getSignerRole()).isEqualTo(SignerRole.ASSIGNEE);
            assertThat(value.getStatus()).isEqualTo(SignerStatus.SIGNED);
            assertThat(value.getUserKeycloakId()).isEqualTo("user-1");
        });

        ArgumentCaptor<SignatureEvidenceService.Draft> drafts = ArgumentCaptor.forClass(SignatureEvidenceService.Draft.class);
        verify(evidenceService, times(2)).append(drafts.capture(), eq("user-1"));
        SignatureEvidenceService.Draft draft = drafts.getAllValues().get(0);
        assertThat(draft.purpose()).isEqualTo(SignaturePurpose.ASSIGNMENT_DECISION);
        assertThat(draft.method()).isEqualTo(SignatureEvidenceMethod.EMAIL_OTP);
        assertThat(draft.documentHash()).isEqualTo(HASH_5);
        assertThat(draft.declarationText()).isEqualTo(prepared.declarations().get(0));
        assertThat(draft.destinationEmail()).isEqualTo("mario.rossi@example.org");
        assertThat(draft.challengeId()).isEqualTo(77L);
        assertThat(draft.sessionId()).isEqualTo("session-1");
        assertThat(draft.ipAddress()).isEqualTo("192.0.2.10");
    }

    private static DecisionCheck check(long assignmentId, String name, String number, String hash, InventoryDecisionRequest request) {
        InventoryItem item = new InventoryItem();
        item.setName(name);
        item.setInventoryNumber(number);
        InventoryAssignment assignment = new InventoryAssignment();
        assignment.setId(assignmentId);
        assignment.setItem(item);
        InventoryAssignmentRevision revision = new InventoryAssignmentRevision();
        revision.setRevisionNumber(2);
        revision.setSnapshotHash(hash);
        String reason = request.rejectionReason() == null ? null : request.rejectionReason().trim();
        return new DecisionCheck(assignment, revision, request.decision(), request.decision() == InventoryDecisionType.REJECTED ? reason : null);
    }

    private static ChallengeItem item(Long assignmentId, String hash, InventoryDecisionType decision, String reason) {
        return new ChallengeItem(assignmentId, hash, decision, reason);
    }

    private static <T extends com.fundaro.zodiac.taurus.domain.inventory.AuditedEntity> T withId(T entity, long id) {
        entity.setId(id);
        return entity;
    }

    private static JwtAuthenticationToken token() {
        Instant now = Instant.now();
        return new JwtAuthenticationToken(Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", "tenant-a")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build());
    }

    private static void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String key) {
        assertThatThrownBy(call).isInstanceOfSatisfying(RequestAlertException.class, exception -> assertThat(exception.getErrorKey()).isEqualTo(key));
    }
}
