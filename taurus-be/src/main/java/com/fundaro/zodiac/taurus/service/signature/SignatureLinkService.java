package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.signature.SignatureAct;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActSigner;
import com.fundaro.zodiac.taurus.domain.signature.SignatureActStatus;
import com.fundaro.zodiac.taurus.domain.signature.SignatureLink;
import com.fundaro.zodiac.taurus.domain.signature.SignatureLinkRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureActSignerRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureChallengeRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureLinkRegistryRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureLinkRepository;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.LinkView;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Links sent by e-mail to a parent or guardian without an account: one signer of one act each, valid for a few days. A
 * new link revokes the previous one; the e-mail leaves after the commit and only its acceptance by the SMTP server is
 * recorded. Every change updates the tenant table and the public registry in the same transaction.
 */
@Service
public class SignatureLinkService {

    private static final Logger LOG = LoggerFactory.getLogger(SignatureLinkService.class);
    /**
     * Writes of the last use of a link are spaced out: the page reads the link at every request.
     */
    private static final Duration LAST_USE_PRECISION = Duration.ofMinutes(1);

    private final SignatureLinkRepository linkRepository;
    private final SignatureLinkRegistryRepository registryRepository;
    private final SignatureActSignerRepository signerRepository;
    private final SignatureActRepository actRepository;
    private final SignatureChallengeRepository challengeRepository;
    private final TenantsRepository tenantsRepository;
    private final SignatureLinkTokens tokens;
    private final SignatureMailService mailService;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final TenantTransactionExecutor tenantTransactionExecutor;
    private final SignatureMetrics metrics;
    private final ApplicationProperties.SignatureProperties properties;

    public SignatureLinkService(
        SignatureLinkRepository linkRepository,
        SignatureLinkRegistryRepository registryRepository,
        SignatureActSignerRepository signerRepository,
        SignatureActRepository actRepository,
        SignatureChallengeRepository challengeRepository,
        TenantsRepository tenantsRepository,
        SignatureLinkTokens tokens,
        SignatureMailService mailService,
        TenantTimeZoneService tenantTimeZoneService,
        TenantTransactionExecutor tenantTransactionExecutor,
        SignatureMetrics metrics,
        ApplicationProperties applicationProperties
    ) {
        this.linkRepository = linkRepository;
        this.registryRepository = registryRepository;
        this.signerRepository = signerRepository;
        this.actRepository = actRepository;
        this.challengeRepository = challengeRepository;
        this.tenantsRepository = tenantsRepository;
        this.tokens = tokens;
        this.mailService = mailService;
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
        this.metrics = metrics;
        this.properties = applicationProperties.getSignature();
    }

    /**
     * What the e-mail of a link names: the sheet, the operation of the act and the minor the parent signs for.
     */
    public record LinkContent(String sheetNumber, String operation, String minorName) {
    }

    /**
     * A link created in the current transaction, with the address of the page to send: the token exists only in memory.
     */
    public record PendingLink(
        Long linkId,
        String tenantCode,
        String tenantName,
        String destination,
        LinkContent content,
        String url,
        ZonedDateTime expiresAt,
        ZoneId zone,
        boolean signed
    ) {
        @Override
        public String toString() {
            return "PendingLink[linkId=" + linkId + ", signed=" + signed + "]";
        }
    }

    /**
     * Creates a link for a signer without an account and revokes the previous ones. {@code signed} links open an act
     * already signed, to download its documents.
     */
    public PendingLink create(SignatureActSigner signer, LinkContent content, boolean signed, String actor) {
        if (signer.getUserKeycloakId() != null || signer.getEmail() == null) {
            throw new IllegalStateException("Only a signer without an account receives a link");
        }
        Tenants tenant = currentTenant();
        ZonedDateTime now = ZonedDateTime.now();
        revoke(List.of(signer), tenant.getId(), now, actor);
        SignatureLinkTokens.Token token = tokens.generate();
        SignatureLink link = new SignatureLink();
        link.initializeAudit(actor);
        link.setSignerId(signer.getId());
        link.setTokenFingerprint(SignatureLinkTokens.fingerprint(token.digest()));
        link.setExpiresAt(now.plus(properties.getLinkValidity()));
        link = linkRepository.saveAndFlush(link);
        SignatureLinkRegistry route = new SignatureLinkRegistry();
        route.setTokenDigest(token.digest());
        route.setTenantId(tenant.getId());
        route.setLinkId(link.getId());
        route.setExpiresAt(link.getExpiresAt());
        registryRepository.save(route);
        metrics.link(signed ? "issuedSigned" : "issued");
        LOG.info("signature_link_issued linkId={} signerId={} signed={}", link.getId(), signer.getId(), signed);
        return new PendingLink(
            link.getId(),
            tenant.getCode(),
            Objects.requireNonNullElse(SignatureSupport.trimToNull(tenant.getName()), tenant.getCode()),
            signer.getEmail(),
            content,
            properties.getPublicBaseUrl().replaceAll("/+$", "") + "/firma/" + token.value(),
            link.getExpiresAt(),
            tenantTimeZoneService.currentZoneId(),
            signed
        );
    }

    /**
     * Creates a link and sends it once the current transaction is committed. A refused e-mail is logged: the link stays
     * unsent and the administrators can send a new one.
     */
    public void createAndSendAfterCommit(SignatureActSigner signer, LinkContent content, boolean signed, String actor) {
        PendingLink pending = create(signer, content, signed, actor);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deliver(pending);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliver(pending);
            }
        });
    }

    /**
     * Sends the e-mail of a link and records when the SMTP server accepted it; returns {@code false} when it refused.
     */
    public boolean deliver(PendingLink pending) {
        try {
            mailService.sendLink(new SignatureMailService.LinkMessage(
                pending.destination(),
                pending.tenantName(),
                pending.content().operation(),
                pending.content().sheetNumber(),
                pending.content().minorName(),
                pending.url(),
                pending.expiresAt(),
                pending.zone(),
                pending.signed()
            ));
        } catch (MailException | IllegalStateException exception) {
            metrics.link("mailFailed");
            LOG.error("signature_link_mail_failed linkId={}", pending.linkId(), exception);
            return false;
        }
        try {
            tenantTransactionExecutor.execute(pending.tenantCode(), () -> linkRepository.findById(pending.linkId()).ifPresent(link -> {
                link.setSentAt(ZonedDateTime.now());
                link.touchAudit(SignatureLinkSession.actor(link.getId()));
                linkRepository.save(link);
            }));
        } catch (RuntimeException exception) {
            LOG.warn("signature_link_sent_not_recorded linkId={}", pending.linkId(), exception);
        }
        LOG.info("signature_link_sent linkId={}", pending.linkId());
        return true;
    }

    /**
     * Revokes the links still valid of the signers.
     */
    public void revoke(Collection<SignatureActSigner> signers, String actor) {
        if (signers.stream().allMatch(signer -> signer.getUserKeycloakId() != null)) return;
        revoke(signers, currentTenant().getId(), ZonedDateTime.now(), actor);
    }

    /**
     * Revokes the links of the signers and removes the challenges confirmed through them, which hold the address of the
     * parent.
     */
    public void purge(Collection<SignatureActSigner> signers, String actor) {
        List<Long> ids = signers.stream().map(SignatureActSigner::getId).toList();
        if (ids.isEmpty()) return;
        List<SignatureLink> links = linkRepository.findAllBySignerIdInAndDeletedFalseOrderByIdAsc(ids);
        if (links.isEmpty()) return;
        revoke(signers, actor);
        challengeRepository.deleteAllByLinkIds(links.stream().map(SignatureLink::getId).toList());
    }

    /**
     * The last link of every signer without an account, as shown to the administrators.
     */
    public Map<Long, LinkView> views(Collection<SignatureActSigner> signers) {
        List<Long> ids = signers.stream().filter(signer -> signer.getUserKeycloakId() == null).map(SignatureActSigner::getId).toList();
        Map<Long, LinkView> views = new HashMap<>();
        if (ids.isEmpty()) return views;
        ZonedDateTime now = ZonedDateTime.now();
        for (SignatureLink link : linkRepository.findAllBySignerIdInAndDeletedFalseOrderByIdAsc(ids)) {
            views.put(link.getSignerId(), new LinkView(link.getTokenFingerprint(), link.getSentAt(), link.getExpiresAt(), link.getLastUsedAt(), link.isActive(now)));
        }
        return views;
    }

    /**
     * Opens the link of a registry row in the transaction of its tenant: the link must be valid and match the token,
     * its signer must have no account and its act must still be in Taurus. Records the last use.
     */
    public SignatureLinkSession open(SignatureLinkRegistry route, String digest, String tenantCode, ZonedDateTime now) {
        SignatureLink link = linkRepository.findById(route.getLinkId())
            .filter(value -> value.isActive(now) && SignatureLinkTokens.matches(value.getTokenFingerprint(), digest))
            .orElseThrow(SignatureLinkUnavailableException::new);
        SignatureActSigner signer = signerRepository.findById(link.getSignerId())
            .filter(value -> !value.isDeleted() && value.getUserKeycloakId() == null && value.getEmail() != null)
            .orElseThrow(SignatureLinkUnavailableException::new);
        SignatureAct act = actRepository.findByIdAndDeletedFalse(signer.getActId())
            .filter(value -> LoanSheetSignatureService.SHEET_ACTS.contains(value.getActType()))
            .filter(value -> value.getStatus() != SignatureActStatus.CANCELLED && value.getPurgedAt() == null)
            .orElseThrow(SignatureLinkUnavailableException::new);
        if (link.getLastUsedAt() == null || link.getLastUsedAt().isBefore(now.minus(LAST_USE_PRECISION))) {
            link.setLastUsedAt(now);
            link.touchAudit(SignatureLinkSession.actor(link.getId()));
            linkRepository.save(link);
        }
        return new SignatureLinkSession(tenantCode, link.getId(), signer.getId(), act.getId(), link.getTokenFingerprint(), link.getExpiresAt());
    }

    /**
     * Checks, in the current transaction, that the link of a session is still valid: a confirmation through a link
     * revoked meanwhile is refused.
     */
    public void requireActive(SignatureLinkSession session) {
        linkRepository.findForUpdate(session.linkId())
            .filter(link -> link.isActive(ZonedDateTime.now()))
            .orElseThrow(SignatureLinkUnavailableException::new);
    }

    /**
     * Removes from the public registry the rows of revoked or expired links.
     */
    public int removeInactiveRoutes(ZonedDateTime now) {
        return tenantTransactionExecutor.execute((String) null, () -> registryRepository.deleteInactive(now));
    }

    private void revoke(Collection<SignatureActSigner> signers, Long tenantId, ZonedDateTime now, String actor) {
        for (SignatureActSigner signer : signers) {
            if (signer.getUserKeycloakId() != null) continue;
            for (SignatureLink link : linkRepository.findActiveBySigner(signer.getId(), now)) {
                link.setRevokedAt(now);
                link.touchAudit(actor);
                linkRepository.save(link);
                registryRepository.revoke(tenantId, link.getId(), now);
                metrics.link("revoked");
                LOG.info("signature_link_revoked linkId={}", link.getId());
            }
        }
    }

    private Tenants currentTenant() {
        String code = TenantContext.getTenantCode().orElseThrow(() -> new IllegalStateException("No tenant for the signature link"));
        return tenantsRepository.findByCodeAndDeletedFalse(code).orElseThrow(() -> new IllegalStateException("Unknown tenant " + code));
    }
}
