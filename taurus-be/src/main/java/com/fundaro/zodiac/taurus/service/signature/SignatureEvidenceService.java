package com.fundaro.zodiac.taurus.service.signature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fundaro.zodiac.taurus.domain.signature.SignatureChainHead;
import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidence;
import com.fundaro.zodiac.taurus.domain.signature.SignatureEvidenceMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.repository.signature.SignatureChainHeadRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureEvidenceRepository;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ChainVerification;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evidence of the signatures, chained within the tenant: every evidence stores the hash of the previous one and its
 * own hash, the SHA-256 of the canonical JSON of its fields. Personal data have their own digest, included in the
 * evidence hash, so the chain stays fully verifiable after they are removed.
 */
@Service
@Transactional
public class SignatureEvidenceService {

    private static final Logger LOG = LoggerFactory.getLogger(SignatureEvidenceService.class);
    static final String GENESIS_HASH = "0".repeat(64);
    private static final int PAGE_SIZE = 500;
    private static final ObjectMapper CANONICAL = new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final SignatureEvidenceRepository evidenceRepository;
    private final SignatureChainHeadRepository headRepository;

    public SignatureEvidenceService(SignatureEvidenceRepository evidenceRepository, SignatureChainHeadRepository headRepository) {
        this.evidenceRepository = evidenceRepository;
        this.headRepository = headRepository;
    }

    /**
     * Appends the evidence of a signature to the chain of the tenant, locking the chain head.
     */
    public SignatureEvidence append(Draft draft, String actor) {
        SignatureChainHead head = headRepository.findForUpdate()
            .orElseThrow(() -> new IllegalStateException("The signature chain head of the tenant is missing"));
        SignatureEvidence evidence = new SignatureEvidence();
        evidence.initializeAudit(actor);
        evidence.setSequence(head.getLastSequence() + 1);
        evidence.setPreviousHash(head.getLastHash());
        evidence.setActId(draft.actId());
        evidence.setDocumentId(draft.documentId());
        evidence.setSignerId(draft.signerId());
        evidence.setPurpose(draft.purpose());
        evidence.setMethod(draft.method());
        evidence.setDocumentHash(draft.documentHash());
        evidence.setDeclarationText(draft.declarationText());
        evidence.setDeclarationHash(SignatureSupport.sha256(draft.declarationText()));
        evidence.setRevocationCheck(draft.revocationCheck());
        evidence.setChallengeId(draft.challengeId());
        // PostgreSQL keeps microseconds: the hash must be computed on the value read back.
        evidence.setSignedAt(Instant.now().truncatedTo(ChronoUnit.MICROS).atZone(ZoneOffset.UTC));
        evidence.setSignerName(draft.signerName());
        evidence.setSignerKeycloakId(draft.signerKeycloakId());
        evidence.setDestinationEmail(draft.destinationEmail());
        evidence.setCertificateSubject(draft.certificateSubject());
        evidence.setCertificateIssuer(draft.certificateIssuer());
        evidence.setCertificateSerial(draft.certificateSerial());
        evidence.setCertificateTaxCode(draft.certificateTaxCode());
        evidence.setSessionId(draft.sessionId());
        evidence.setIpAddress(draft.ipAddress());
        evidence.setUserAgent(draft.userAgent());
        evidence.setPersonalDataHash(personalDataHash(evidence));
        evidence.setEvidenceHash(evidenceHash(evidence));
        SignatureEvidence saved = evidenceRepository.save(evidence);
        head.setLastSequence(saved.getSequence());
        head.setLastHash(saved.getEvidenceHash());
        head.touchAudit(actor);
        headRepository.save(head);
        return saved;
    }

    /**
     * Removes the personal data of the evidence not yet purged.
     */
    public void purge(List<SignatureEvidence> evidence, ZonedDateTime at, String actor) {
        evidence.stream().filter(value -> value.getPurgedAt() == null).forEach(value -> {
            value.purgePersonalData(at, actor);
            evidenceRepository.save(value);
        });
    }

    /**
     * Verification of the chain requested by an administrator of the tenant.
     */
    @Transactional(readOnly = true)
    public ChainVerification verifyChain(org.springframework.security.authentication.AbstractAuthenticationToken token) {
        SignatureSupport.tenant(token);
        ChainVerification result = verify();
        LOG.info("signature_chain_verified valid={} checked={}", result.valid(), result.checked());
        return result;
    }

    /**
     * Recomputes the whole chain of the tenant and returns the first position that is not consistent.
     */
    @Transactional(readOnly = true)
    public ChainVerification verify() {
        long expectedSequence = 1;
        String previousHash = GENESIS_HASH;
        long checked = 0;
        List<SignatureEvidence> page = evidenceRepository.findChainPage(0, PageRequest.of(0, PAGE_SIZE));
        while (!page.isEmpty()) {
            for (SignatureEvidence evidence : page) {
                String problem = problem(evidence, expectedSequence, previousHash);
                if (problem != null) {
                    LOG.warn("signature_chain_inconsistent sequence={} problem={}", evidence.getSequence(), problem);
                    return new ChainVerification(false, checked, evidence.getSequence(), problem);
                }
                checked++;
                expectedSequence++;
                previousHash = evidence.getEvidenceHash();
            }
            page = evidenceRepository.findChainPage(page.get(page.size() - 1).getSequence(), PageRequest.of(0, PAGE_SIZE));
        }
        SignatureChainHead head = headRepository.findAll().stream().filter(value -> !value.isDeleted()).findFirst().orElse(null);
        if (head == null || head.getLastSequence() != expectedSequence - 1 || !head.getLastHash().equals(previousHash)) {
            LOG.warn("signature_chain_inconsistent sequence={} problem=head", expectedSequence);
            return new ChainVerification(false, checked, expectedSequence, "head");
        }
        return new ChainVerification(true, checked, null, null);
    }

    private static String problem(SignatureEvidence evidence, long expectedSequence, String previousHash) {
        if (evidence.getSequence() != expectedSequence) return "sequence";
        if (!previousHash.equals(evidence.getPreviousHash())) return "previousHash";
        if (!evidenceHash(evidence).equals(evidence.getEvidenceHash())) return "evidenceHash";
        if (evidence.getPurgedAt() == null && !personalDataHash(evidence).equals(evidence.getPersonalDataHash())) return "personalData";
        return null;
    }

    static String personalDataHash(SignatureEvidence evidence) {
        Map<String, Object> values = new TreeMap<>();
        values.put("declarationText", evidence.getDeclarationText());
        values.put("signerName", evidence.getSignerName());
        values.put("signerKeycloakId", evidence.getSignerKeycloakId());
        values.put("destinationEmail", evidence.getDestinationEmail());
        values.put("certificateSubject", evidence.getCertificateSubject());
        values.put("certificateIssuer", evidence.getCertificateIssuer());
        values.put("certificateSerial", evidence.getCertificateSerial());
        values.put("certificateTaxCode", evidence.getCertificateTaxCode());
        values.put("sessionId", evidence.getSessionId());
        values.put("ipAddress", evidence.getIpAddress());
        values.put("userAgent", evidence.getUserAgent());
        return SignatureSupport.sha256(canonical(values));
    }

    static String evidenceHash(SignatureEvidence evidence) {
        Map<String, Object> values = new TreeMap<>();
        values.put("sequence", evidence.getSequence());
        values.put("previousHash", evidence.getPreviousHash());
        values.put("actId", evidence.getActId());
        values.put("documentId", evidence.getDocumentId());
        values.put("signerId", evidence.getSignerId());
        values.put("purpose", evidence.getPurpose().name());
        values.put("method", evidence.getMethod().name());
        values.put("documentHash", evidence.getDocumentHash());
        values.put("declarationHash", evidence.getDeclarationHash());
        values.put("revocationCheck", evidence.getRevocationCheck());
        values.put("challengeId", evidence.getChallengeId());
        values.put("signedAt", DateTimeFormatter.ISO_INSTANT.format(evidence.getSignedAt().toInstant().truncatedTo(ChronoUnit.MICROS)));
        values.put("personalDataHash", evidence.getPersonalDataHash());
        return SignatureSupport.sha256(canonical(values));
    }

    private static String canonical(Map<String, Object> values) {
        try {
            return CANONICAL.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize the signature evidence", exception);
        }
    }

    /**
     * Data of a new evidence; the certificate fields are filled for CieSign signatures only.
     */
    public record Draft(
        Long actId,
        Long documentId,
        Long signerId,
        SignaturePurpose purpose,
        SignatureEvidenceMethod method,
        String documentHash,
        String declarationText,
        String signerName,
        String signerKeycloakId,
        String destinationEmail,
        String certificateSubject,
        String certificateIssuer,
        String certificateSerial,
        String certificateTaxCode,
        String revocationCheck,
        Long challengeId,
        String sessionId,
        String ipAddress,
        String userAgent
    ) {
        @Override
        public String toString() {
            return "Draft[actId=" + actId + ", documentId=" + documentId + ", purpose=" + purpose + ", method=" + method + "]";
        }
    }
}
