package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureAct;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActSigner;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActType;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActSignerRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureChallengeRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureEvidenceRepository;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removal of the signature data of a member by the GDPR erasure: Taurus does not keep signed acts on behalf of the
 * tenant, so personal data of the evidence, signers and challenges are removed at once. Hashes and links of the
 * evidence stay, and the chain remains verifiable.
 */
@Service
@Transactional
public class SignatureErasureService {

    private static final Logger LOG = LoggerFactory.getLogger(SignatureErasureService.class);
    static final String REMOVED_NAME = "Dati personali rimossi";

    private final SignatureActRepository actRepository;
    private final SignatureActSignerRepository signerRepository;
    private final SignatureEvidenceRepository evidenceRepository;
    private final SignatureChallengeRepository challengeRepository;
    private final SignatureEvidenceService evidenceService;
    private final SignatureMetrics metrics;

    public SignatureErasureService(
        SignatureActRepository actRepository,
        SignatureActSignerRepository signerRepository,
        SignatureEvidenceRepository evidenceRepository,
        SignatureChallengeRepository challengeRepository,
        SignatureEvidenceService evidenceService,
        SignatureMetrics metrics
    ) {
        this.actRepository = actRepository;
        this.signerRepository = signerRepository;
        this.evidenceRepository = evidenceRepository;
        this.challengeRepository = challengeRepository;
        this.evidenceService = evidenceService;
        this.metrics = metrics;
    }

    public void purgeForUser(String userKeycloakId, String pseudonym, String actor) {
        ZonedDateTime now = ZonedDateTime.now();
        List<SignatureAct> acts = actRepository.findUnpurgedForSigner(userKeycloakId);
        evidenceService.purge(evidenceRepository.findAllBySignerKeycloakIdAndPurgedAtIsNull(userKeycloakId), now, actor);
        for (SignatureActSigner signer : signerRepository.findAllByUserKeycloakIdAndDeletedFalse(userKeycloakId)) {
            signer.setUserKeycloakId(pseudonym);
            signer.setDisplayName(REMOVED_NAME);
            signer.setEmail(null);
            signer.touchAudit(actor);
            signerRepository.save(signer);
        }
        for (SignatureAct act : acts) {
            if (act.getActType() != SignatureActType.ASSIGNMENT_ACKNOWLEDGMENT) continue;
            act.setPurgedAt(now);
            act.touchAudit(actor);
            actRepository.save(act);
        }
        int challenges = challengeRepository.deleteAllByUser(userKeycloakId);
        metrics.purge("gdpr", "done");
        LOG.info("signature_user_purged acts={} challenges={}", acts.size(), challenges);
    }
}
