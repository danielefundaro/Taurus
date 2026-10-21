package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.ActOutcome;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.Requirements;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Signature of a document with CieSign: the holder downloads the frozen document, signs it with the CieSign app or the
 * CIE software and uploads the signed file. The file is verified outside the transactions, because the revocation list
 * can take seconds to download; the signature is then recorded with the act locked. Rejected files are not stored.
 */
@Service
public class CieSignService {

    /**
     * Largest file accepted, 25 MB.
     */
    public static final long MAX_BYTES = 25L * 1024 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(CieSignService.class);

    private final LoanSheetSigning signing;
    private final CieSignVerifier verifier;
    private final SignatureSigners signers;
    private final SignatureRequirementsService requirementsService;
    private final SignatureMetrics metrics;
    private final TransactionTemplate transactions;

    public CieSignService(
        LoanSheetSigning signing,
        CieSignVerifier verifier,
        SignatureSigners signers,
        SignatureRequirementsService requirementsService,
        SignatureMetrics metrics,
        PlatformTransactionManager transactionManager
    ) {
        this.signing = signing;
        this.verifier = verifier;
        this.signers = signers;
        this.requirementsService = requirementsService;
        this.metrics = metrics;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * A document of an act signed with CieSign by the authenticated holder.
     */
    public ActOutcome sign(long actId, SignatureDocumentRole role, byte[] file, HttpServletRequest httpRequest, AbstractAuthenticationToken token) {
        return sign(actId, role, () -> file, httpRequest, token);
    }

    /**
     * The uploaded file of a document signed with CieSign by the authenticated holder; an empty or too large file is a
     * rejection like the others.
     */
    public ActOutcome sign(long actId, SignatureDocumentRole role, MultipartFile file, HttpServletRequest httpRequest, AbstractAuthenticationToken token) {
        return sign(actId, role, () -> content(file), httpRequest, token);
    }

    /**
     * A document of the act of a signature link signed with CieSign by the parent or guardian.
     */
    public ActOutcome signForLink(SignatureLinkSession session, SignatureDocumentRole role, byte[] file, HttpServletRequest httpRequest) {
        return sign(session.actId(), role, () -> file, () -> signers.link(session, httpRequest));
    }

    /**
     * The uploaded file of a document of the act of a signature link signed with CieSign by the parent or guardian.
     */
    public ActOutcome signForLink(SignatureLinkSession session, SignatureDocumentRole role, MultipartFile file, HttpServletRequest httpRequest) {
        return sign(session.actId(), role, () -> content(file), () -> signers.link(session, httpRequest));
    }

    private ActOutcome sign(long actId, SignatureDocumentRole role, Supplier<byte[]> file, HttpServletRequest httpRequest, AbstractAuthenticationToken token) {
        String tenant = SignatureSupport.tenant(token);
        String userId = SignatureSupport.actor(token);
        return sign(actId, role, file, () -> signers.user(userId, token, httpRequest, tenant));
    }

    /**
     * Content of an uploaded file, refused without reading it when larger than {@link #MAX_BYTES}.
     */
    public static byte[] content(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "The signed file is empty", "signature.cieSign.invalidSignature");
        }
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read the signed file", exception);
        }
    }

    /**
     * Error of a file larger than {@link #MAX_BYTES}.
     */
    public static RequestAlertException tooLarge() {
        return SignatureSupport.error(HttpStatus.PAYLOAD_TOO_LARGE, "The signed file exceeds 25 MB", "signature.cieSign.tooLarge")
            .with("maxMegabytes", (int) (MAX_BYTES / (1024 * 1024)));
    }

    private ActOutcome sign(long actId, SignatureDocumentRole role, Supplier<byte[]> upload, Supplier<Signer> signer) {
        try {
            if (!verifier.available()) throw CieSignVerifier.notConfigured();
            byte[] file = upload.get();
            if (file == null || file.length == 0) {
                throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "The signed file is empty", "signature.cieSign.invalidSignature");
            }
            if (file.length > MAX_BYTES) throw tooLarge();
            requireRequirements();
            LoanSheetSigning.CieSignTarget target = Objects.requireNonNull(transactions.execute(status -> signing.cieSignTarget(actId, role, signer.get())));
            CieSignVerifier.Result certificate = verifier.verify(target.frozen(), file, target.identity(), Instant.now());
            ActOutcome outcome = Objects.requireNonNull(
                transactions.execute(status -> signing.recordCieSign(actId, role, target.frozenHash(), file, certificate, signer.get()))
            );
            boolean revocationChecked = !CieRevocationChecker.UNAVAILABLE.equals(certificate.revocationCheck());
            metrics.cieSign(revocationChecked ? "accepted" : "acceptedRevocationUnavailable");
            LOG.info("signature_ciesign_accepted actId={} role={} revocationCheck={}", actId, role, certificate.revocationCheck());
            return outcome;
        } catch (RequestAlertException exception) {
            String key = exception.getErrorKey();
            if (key != null && key.startsWith("signature.cieSign.")) {
                metrics.cieSign(key.substring("signature.cieSign.".length()));
                LOG.info("signature_ciesign_rejected actId={} role={} key={}", actId, role, key);
            }
            throw exception;
        }
    }

    private void requireRequirements() {
        Requirements requirements = requirementsService.requirements();
        if (!requirements.satisfied()) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "Electronic signature requirements are missing", "signature.settings.requirementsMissing")
                .with("missing", requirements.missing());
        }
    }
}
