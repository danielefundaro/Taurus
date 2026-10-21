package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.signature.*;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActSignerRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureChallengeRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureEvidenceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignatureErasureServiceTest {

    @Mock
    SignatureActRepository actRepository;
    @Mock
    SignatureActSignerRepository signerRepository;
    @Mock
    SignatureEvidenceRepository evidenceRepository;
    @Mock
    SignatureChallengeRepository challengeRepository;
    @Mock
    SignatureEvidenceService evidenceService;

    @Test
    void removesThePersonalDataOfTheMemberAtOnce() {
        SignatureAct acknowledgment = act(SignatureActType.ASSIGNMENT_ACKNOWLEDGMENT);
        SignatureAct issue = act(SignatureActType.LOAN_SHEET_ISSUE);
        SignatureActSigner signer = new SignatureActSigner();
        signer.setUserKeycloakId("user-1");
        signer.setDisplayName("Mario Rossi");
        signer.setEmail("mario.rossi@example.org");
        List<SignatureEvidence> evidence = List.of(new SignatureEvidence());
        when(actRepository.findUnpurgedForSigner("user-1")).thenReturn(List.of(acknowledgment, issue));
        when(evidenceRepository.findAllBySignerKeycloakIdAndPurgedAtIsNull("user-1")).thenReturn(evidence);
        when(signerRepository.findAllByUserKeycloakIdAndDeletedFalse("user-1")).thenReturn(List.of(signer));
        when(challengeRepository.deleteAllByUser("user-1")).thenReturn(2);
        SignatureErasureService service = new SignatureErasureService(
            actRepository, signerRepository, evidenceRepository, challengeRepository, evidenceService, new SignatureMetrics(new SimpleMeterRegistry())
        );

        service.purgeForUser("user-1", "erased:1", "admin-1");

        verify(evidenceService).purge(eq(evidence), any(), eq("admin-1"));
        assertThat(signer.getUserKeycloakId()).isEqualTo("erased:1");
        assertThat(signer.getDisplayName()).isEqualTo("Dati personali rimossi");
        assertThat(signer.getEmail()).isNull();
        assertThat(acknowledgment.getPurgedAt()).isNotNull();
        assertThat(issue.getPurgedAt()).isNull();
        verify(challengeRepository).deleteAllByUser("user-1");
    }

    private static SignatureAct act(SignatureActType type) {
        SignatureAct act = new SignatureAct();
        act.setActType(type);
        return act;
    }
}
