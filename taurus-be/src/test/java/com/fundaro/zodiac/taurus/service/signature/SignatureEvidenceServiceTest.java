package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureChainHead;
import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidence;
import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidenceMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.repository.signature.SignatureChainHeadRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureEvidenceRepository;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ChainVerification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignatureEvidenceServiceTest {

    @Mock
    SignatureEvidenceRepository evidenceRepository;
    @Mock
    SignatureChainHeadRepository headRepository;

    private final List<SignatureEvidence> chain = new ArrayList<>();
    private SignatureChainHead head;
    private SignatureEvidenceService service;

    @BeforeEach
    void setUp() {
        head = new SignatureChainHead();
        head.setLastSequence(0);
        head.setLastHash(SignatureEvidenceService.GENESIS_HASH);
        when(headRepository.findForUpdate()).thenReturn(Optional.of(head));
        when(headRepository.findAll()).thenAnswer(invocation -> List.of(head));
        when(evidenceRepository.save(any(SignatureEvidence.class))).thenAnswer(invocation -> {
            SignatureEvidence evidence = invocation.getArgument(0);
            if (evidence.getId() == null) {
                evidence.setId((long) chain.size() + 1);
                chain.add(evidence);
            }
            return evidence;
        });
        when(evidenceRepository.findChainPage(anyLong(), any(Pageable.class))).thenAnswer(invocation -> {
            long after = invocation.getArgument(0);
            Pageable page = invocation.getArgument(1);
            return chain.stream().filter(evidence -> evidence.getSequence() > after).limit(page.getPageSize()).toList();
        });
        service = new SignatureEvidenceService(evidenceRepository, headRepository);
    }

    @Test
    void chainsEveryEvidenceToThePreviousOne() {
        SignatureEvidence first = service.append(draft(1L, "Mario Rossi"), "user-1");
        SignatureEvidence second = service.append(draft(2L, "Anna Bianchi"), "user-2");

        assertThat(first.getSequence()).isEqualTo(1);
        assertThat(first.getPreviousHash()).isEqualTo(SignatureEvidenceService.GENESIS_HASH);
        assertThat(second.getSequence()).isEqualTo(2);
        assertThat(second.getPreviousHash()).isEqualTo(first.getEvidenceHash());
        assertThat(first.getDeclarationHash()).isEqualTo(SignatureSupport.sha256(first.getDeclarationText()));
        assertThat(first.getSignedAt().getNano() % 1000).isZero();
        assertThat(head.getLastSequence()).isEqualTo(2);
        assertThat(head.getLastHash()).isEqualTo(second.getEvidenceHash());
        assertThat(service.verify()).isEqualTo(new ChainVerification(true, 2, null, null));
    }

    @Test
    void detectsAChangeOfThePersonalDataOrOfTheOtherFields() {
        service.append(draft(1L, "Mario Rossi"), "user-1");
        SignatureEvidence second = service.append(draft(2L, "Anna Bianchi"), "user-2");
        service.append(draft(3L, "Carla Verdi"), "user-3");

        second.setSignerName("Altra persona");
        assertThat(service.verify()).isEqualTo(new ChainVerification(false, 1, 2L, "personalData"));

        second.setSignerName("Anna Bianchi");
        second.setDocumentHash("f".repeat(64));
        assertThat(service.verify()).isEqualTo(new ChainVerification(false, 1, 2L, "evidenceHash"));
    }

    @Test
    void detectsARemovedOrReplacedEvidence() {
        service.append(draft(1L, "Mario Rossi"), "user-1");
        service.append(draft(2L, "Anna Bianchi"), "user-2");
        SignatureEvidence third = service.append(draft(3L, "Carla Verdi"), "user-3");

        chain.remove(1);
        assertThat(service.verify()).isEqualTo(new ChainVerification(false, 1, 3L, "sequence"));

        chain.clear();
        head.setLastSequence(0);
        head.setLastHash(SignatureEvidenceService.GENESIS_HASH);
        SignatureEvidence first = service.append(draft(1L, "Mario Rossi"), "user-1");
        third.setSequence(2);
        chain.add(third);
        head.setLastSequence(2);
        head.setLastHash(third.getEvidenceHash());
        assertThat(service.verify().problem()).isEqualTo("previousHash");
        assertThat(first.getSequence()).isEqualTo(1);
    }

    @Test
    void detectsAHeadThatDoesNotMatchTheLastEvidence() {
        service.append(draft(1L, "Mario Rossi"), "user-1");
        head.setLastSequence(5);

        assertThat(service.verify()).isEqualTo(new ChainVerification(false, 1, 2L, "head"));
    }

    @Test
    void staysVerifiableAfterThePersonalDataAreRemoved() {
        SignatureEvidence first = service.append(draft(1L, "Mario Rossi"), "user-1");
        service.append(draft(2L, "Anna Bianchi"), "user-2");
        String hash = first.getEvidenceHash();

        service.purge(List.of(first), ZonedDateTime.now(ZoneId.of("UTC")), "gdpr");

        assertThat(first.getPurgedAt()).isNotNull();
        assertThat(first.getSignerName()).isNull();
        assertThat(first.getSignerKeycloakId()).isNull();
        assertThat(first.getDestinationEmail()).isNull();
        assertThat(first.getDeclarationText()).isNull();
        assertThat(first.getIpAddress()).isNull();
        assertThat(first.getUserAgent()).isNull();
        assertThat(first.getSessionId()).isNull();
        assertThat(first.getEvidenceHash()).isEqualTo(hash);
        assertThat(service.verify().valid()).isTrue();
    }

    @Test
    void readsBackTheSigningInstantInAnyZone() {
        SignatureEvidence evidence = service.append(draft(1L, "Mario Rossi"), "user-1");
        evidence.setSignedAt(evidence.getSignedAt().withZoneSameInstant(ZoneId.of("Europe/Rome")));

        assertThat(SignatureEvidenceService.evidenceHash(evidence)).isEqualTo(evidence.getEvidenceHash());
    }

    private static SignatureEvidenceService.Draft draft(long actId, String name) {
        return new SignatureEvidenceService.Draft(
            actId,
            actId * 10,
            actId * 100,
            SignaturePurpose.ASSIGNMENT_DECISION,
            SignatureEvidenceMethod.EMAIL_OTP,
            "a".repeat(64),
            "Ho preso visione dei dati e li accetto.",
            name,
            "user-" + actId,
            "socio" + actId + "@example.org",
            null,
            null,
            null,
            null,
            null,
            actId + 1000,
            "session-" + actId,
            "192.0.2." + actId,
            "Mozilla/5.0"
        );
    }
}
