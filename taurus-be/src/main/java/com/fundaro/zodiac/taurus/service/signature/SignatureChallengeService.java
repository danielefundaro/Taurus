package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Preferences;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.signature.SignatureChallenge;
import com.fundaro.zodiac.taurus.domain.signature.SignatureChallengeStatus;
import com.fundaro.zodiac.taurus.domain.signature.SignatureDocumentRole;
import com.fundaro.zodiac.taurus.domain.signature.SignatureMethod;
import com.fundaro.zodiac.taurus.domain.signature.SignaturePurpose;
import com.fundaro.zodiac.taurus.repository.PreferencesRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.signature.SignatureChallengeRepository;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.signature.SignatureDtos.*;
import com.fundaro.zodiac.taurus.service.impl.InventoryService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Confirmations by code: creation, new code and confirmation. Transactions are explicit because the outcome of a
 * wrong code must be stored even if the request fails, and the e-mail is sent only after the challenge is saved. The
 * code is sent by e-mail or comes from the authentication app, which Keycloak checks. A parent or guardian without an
 * account confirms through the signature link, always with a code sent to the address of the link.
 */
@Service
public class SignatureChallengeService {

    /**
     * Key of the preferred method in the user preferences, with values {@code EMAIL} and {@code TOTP}.
     */
    public static final String METHOD_PREFERENCE = "signature.method";

    private static final Logger LOG = LoggerFactory.getLogger(SignatureChallengeService.class);
    /**
     * What a parent or guardian signs through a link: the acceptance of the act and the approval of the clauses.
     */
    private static final Set<SignaturePurpose> LINK_PURPOSES = EnumSet.of(SignaturePurpose.ACCEPTANCE, SignaturePurpose.CLAUSE_APPROVAL);

    private final Map<SignaturePurpose, SignaturePurposeHandler> handlers = new EnumMap<>(SignaturePurpose.class);
    private final SignatureChallengeRepository challengeRepository;
    private final SignatureSigners signers;
    private final SignatureLinkService linkService;
    private final SignatureCodes codes;
    private final SignatureMailService mailService;
    private final KeycloakOtpClient otpClient;
    private final SignatureRequirementsService requirementsService;
    private final UsersRepository usersRepository;
    private final PreferencesRepository preferencesRepository;
    private final TenantsService tenantsService;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final InventoryService inventoryService;
    private final SignatureMetrics metrics;
    private final ApplicationProperties.SignatureProperties properties;
    private final TransactionTemplate transactions;

    public SignatureChallengeService(
        List<SignaturePurposeHandler> handlers,
        SignatureChallengeRepository challengeRepository,
        SignatureSigners signers,
        SignatureLinkService linkService,
        SignatureCodes codes,
        SignatureMailService mailService,
        KeycloakOtpClient otpClient,
        SignatureRequirementsService requirementsService,
        UsersRepository usersRepository,
        PreferencesRepository preferencesRepository,
        TenantsService tenantsService,
        TenantTimeZoneService tenantTimeZoneService,
        InventoryService inventoryService,
        SignatureMetrics metrics,
        ApplicationProperties applicationProperties,
        PlatformTransactionManager transactionManager
    ) {
        handlers.forEach(handler -> this.handlers.put(handler.purpose(), handler));
        this.challengeRepository = challengeRepository;
        this.signers = signers;
        this.linkService = linkService;
        this.codes = codes;
        this.mailService = mailService;
        this.otpClient = otpClient;
        this.requirementsService = requirementsService;
        this.usersRepository = usersRepository;
        this.preferencesRepository = preferencesRepository;
        this.tenantsService = tenantsService;
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.inventoryService = inventoryService;
        this.metrics = metrics;
        this.properties = applicationProperties.getSignature();
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Methods available to the authenticated user: the authentication app is proposed when the user prefers it and has
     * configured it, otherwise the code is sent by e-mail. Keycloak is asked only when the tenant uses the electronic
     * signature, because the pages read the methods to know that.
     */
    public Methods methods(AbstractAuthenticationToken token) {
        SignatureSupport.tenant(token);
        String userId = SignatureSupport.actor(token);
        KeycloakOtpClient.Status totp = inventoryService.electronicSignatureEnabled() ? otpClient.status(userId) : KeycloakOtpClient.Status.UNAVAILABLE;
        return Objects.requireNonNull(transactions.execute(status -> methods(userId, totp)));
    }

    /**
     * Saves the preferred method. The authentication app can be preferred before it is configured: until then the
     * code is sent by e-mail and the profile says so.
     */
    public Methods updatePreference(SignatureMethod preference, AbstractAuthenticationToken token) {
        SignatureSupport.tenant(token);
        String userId = SignatureSupport.actor(token);
        KeycloakOtpClient.Status totp = otpClient.status(userId);
        return Objects.requireNonNull(transactions.execute(status -> {
            ZonedDateTime now = ZonedDateTime.now();
            Preferences row = preferencesRepository.findFirstByUserIdAndKeyAndDeletedFalseOrderByIdDesc(userId, METHOD_PREFERENCE).orElseGet(() -> {
                Preferences created = new Preferences();
                created.setKey(METHOD_PREFERENCE);
                created.setUserId(userId);
                created.setDeleted(false);
                created.setInsertBy(userId);
                created.setInsertDate(now);
                return created;
            });
            row.setValue(preference == SignatureMethod.TOTP ? "TOTP" : "EMAIL");
            row.setEditBy(userId);
            row.setEditDate(now);
            preferencesRepository.save(row);
            LOG.info("signature_method_preference_updated preference={}", preference);
            return methods(userId, totp);
        }));
    }

    /**
     * Creates a challenge for the items and sends the code by e-mail; with the authentication app nothing is sent.
     */
    public Challenge create(ChallengeRequest request, HttpServletRequest httpRequest, AbstractAuthenticationToken token) {
        String tenant = SignatureSupport.tenant(token);
        String userId = SignatureSupport.actor(token);
        requireRequirements();
        if (request.method() != SignatureMethod.EMAIL_OTP) requireTotp(userId);
        return open(Owner.user(userId), tenant, request.purpose(), request.method(), request.items(), () -> signers.user(userId, token, httpRequest, tenant));
    }

    /**
     * Creates the challenge of a parent or guardian through the signature link, for the document of the act of the link
     * with the hash shown, and sends the code to the address of the link.
     */
    public Challenge createForLink(SignatureLinkSession session, LinkChallengeRequest request, HttpServletRequest httpRequest) {
        if (!LINK_PURPOSES.contains(request.purpose())) {
            throw SignatureSupport.error(HttpStatus.BAD_REQUEST, "Unsupported signature purpose " + request.purpose(), "signature.challenge.invalid");
        }
        requireRequirements();
        SignatureDocumentRole role = request.purpose() == SignaturePurpose.CLAUSE_APPROVAL ? SignatureDocumentRole.CLAUSES : SignatureDocumentRole.MAIN;
        List<ChallengeItem> items = List.of(ChallengeItem.document(session.actId(), role, request.documentHash()));
        return open(Owner.link(session.linkId()), session.tenantCode(), request.purpose(), SignatureMethod.EMAIL_OTP, items, () -> signers.link(session, httpRequest));
    }

    private Challenge open(
        Owner owner,
        String tenant,
        SignaturePurpose purpose,
        SignatureMethod method,
        List<ChallengeItem> items,
        Supplier<Signer> signers
    ) {
        boolean email = method == SignatureMethod.EMAIL_OTP;
        SignaturePurposeHandler handler = handler(purpose);
        Created created = Objects.requireNonNull(transactions.execute(status -> {
            Signer signer = signers.get();
            if (email && signer.email() == null) {
                throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "The signer has no e-mail address", "signature.mail.addressMissing");
            }
            ZonedDateTime now = ZonedDateTime.now();
            requireHourlyLimit(owner, now);
            SignaturePurposeHandler.Prepared prepared = handler.prepare(items, signer);
            SignatureChallenge challenge = new SignatureChallenge();
            challenge.initializeAudit(owner.actor());
            challenge.setPurpose(purpose);
            challenge.setMethod(method);
            challenge.setUserKeycloakId(owner.userId());
            challenge.setLinkId(owner.linkId());
            challenge.setDestinationEmail(email ? signer.email() : null);
            challenge.setItemsJson(prepared.itemsJson());
            challenge.setItemsHash(SignatureSupport.sha256(prepared.itemsJson()));
            challenge.setExpiresAt(now.plus(properties.getCodeValidity()));
            challenge.setStatus(SignatureChallengeStatus.OPEN);
            challenge = challengeRepository.saveAndFlush(challenge);
            if (!email) return new Created(challenge, null, prepared);
            String code = newCode(challenge, now);
            return new Created(challengeRepository.save(challenge), code, prepared);
        }));
        if (email) send(created, tenant, owner);
        metrics.challenge(method, "created");
        LOG.info(
            "signature_challenge_created challengeId={} purpose={} method={} link={}",
            created.challenge().getId(),
            purpose,
            method,
            owner.linkId() != null
        );
        return toDto(created.challenge(), created.prepared());
    }

    /**
     * Sends a new code, which replaces the previous one.
     */
    public Challenge resend(long id, AbstractAuthenticationToken token) {
        String tenant = SignatureSupport.tenant(token);
        String userId = SignatureSupport.actor(token);
        return resend(Owner.user(userId), id, tenant, () -> {});
    }

    /**
     * Sends a new code to the address of a signature link.
     */
    public Challenge resendForLink(SignatureLinkSession session, long id) {
        return resend(Owner.link(session.linkId()), id, session.tenantCode(), () -> linkService.requireActive(session));
    }

    private Challenge resend(Owner owner, long id, String tenant, Runnable linkCheck) {
        requireRequirements();
        Object result = transactions.execute(status -> {
            linkCheck.run();
            SignatureChallenge challenge = owned(id, owner);
            if (challenge.getMethod() != SignatureMethod.EMAIL_OTP) {
                return SignatureSupport.error(HttpStatus.BAD_REQUEST, "No code is sent for the authentication app", "signature.challenge.invalid");
            }
            ZonedDateTime now = ZonedDateTime.now();
            RequestAlertException closed = closedError(challenge, now);
            if (closed != null) return closed;
            if (challenge.getSends() >= properties.getMaxSends()) {
                return SignatureSupport.error(HttpStatus.CONFLICT, "No more codes can be sent for this challenge", "signature.challenge.limit")
                    .with("maxSends", properties.getMaxSends());
            }
            ZonedDateTime available = challenge.getLastSentAt() == null ? now : challenge.getLastSentAt().plus(properties.getResendInterval());
            if (now.isBefore(available)) {
                long seconds = Math.max(1, Duration.between(now, available).toSeconds() + 1);
                return SignatureSupport.error(HttpStatus.TOO_MANY_REQUESTS, "A new code was requested too soon", "signature.challenge.resendTooSoon")
                    .with("retryAfterSeconds", seconds);
            }
            challenge.setExpiresAt(now.plus(properties.getCodeValidity()));
            String code = newCode(challenge, now);
            challenge.touchAudit(owner.actor());
            return new Created(challengeRepository.save(challenge), code, handler(challenge.getPurpose()).describe(challenge.getItemsJson()));
        });
        if (result instanceof RequestAlertException exception) throw exception;
        Created created = (Created) result;
        send(created, tenant, owner);
        metrics.challenge(created.challenge().getMethod(), "resent");
        return toDto(created.challenge(), created.prepared());
    }

    /**
     * Confirms the code: a wrong code consumes an attempt, a right one records the signatures and closes the challenge.
     */
    public Confirmation confirm(long id, ConfirmRequest request, HttpServletRequest httpRequest, AbstractAuthenticationToken token) {
        String tenant = SignatureSupport.tenant(token);
        String userId = SignatureSupport.actor(token);
        return confirm(Owner.user(userId), id, request.code(), () -> signers.user(userId, token, httpRequest, tenant));
    }

    /**
     * Confirms the code of a parent or guardian through the signature link, while the link is valid.
     */
    public Confirmation confirmForLink(SignatureLinkSession session, long id, ConfirmRequest request, HttpServletRequest httpRequest) {
        return confirm(Owner.link(session.linkId()), id, request.code(), () -> signers.link(session, httpRequest));
    }

    private Confirmation confirm(Owner owner, long id, String code, Supplier<Signer> signers) {
        Verification verification = Objects.requireNonNull(transactions.execute(status -> verify(id, code, owner)));
        if (verification.error() != null) {
            metrics.challenge(verification.method(), verification.outcome());
            throw verification.error();
        }
        try {
            List<ActOutcome> acts = Objects.requireNonNull(transactions.execute(status -> {
                Signer signer = signers.get();
                SignatureChallenge challenge = owned(id, owner);
                if (challenge.getStatus() != SignatureChallengeStatus.OPEN) {
                    throw SignatureSupport.error(HttpStatus.CONFLICT, "The challenge is already closed", "signature.challenge.closed");
                }
                List<ActOutcome> outcomes = handler(challenge.getPurpose()).confirm(challenge, signer);
                challenge.setStatus(SignatureChallengeStatus.CONFIRMED);
                challenge.setConfirmedAt(ZonedDateTime.now());
                challenge.setCodeDigest(null);
                challenge.touchAudit(owner.actor());
                challengeRepository.save(challenge);
                return outcomes;
            }));
            metrics.challenge(verification.method(), "confirmed");
            LOG.info("signature_challenge_confirmed challengeId={} acts={}", id, acts.size());
            return new Confirmation(id, acts);
        } catch (RequestAlertException exception) {
            transactions.executeWithoutResult(status -> cancel(id, owner.actor()));
            metrics.challenge(verification.method(), "cancelled");
            LOG.info("signature_challenge_cancelled challengeId={} key={}", id, exception.getErrorKey());
            throw exception;
        }
    }

    private Verification verify(long id, String code, Owner owner) {
        SignatureChallenge challenge = owned(id, owner);
        ZonedDateTime now = ZonedDateTime.now();
        RequestAlertException closed = closedError(challenge, now);
        if (closed != null) return new Verification(challenge.getMethod(), "closed", closed);
        if (challenge.getMethod() == SignatureMethod.TOTP) {
            // The subject of the challenge is the authenticated signer: owned() checked it.
            KeycloakOtpClient.Verification result = otpClient.verify(challenge.getUserKeycloakId(), code);
            if (result == KeycloakOtpClient.Verification.UNAVAILABLE) return new Verification(challenge.getMethod(), "unavailable", unavailable());
            if (result == KeycloakOtpClient.Verification.VALID) return new Verification(challenge.getMethod(), "valid", null);
        } else if (codes.matches(binding(challenge), code, challenge.getCodeDigest())) {
            return new Verification(challenge.getMethod(), "valid", null);
        }
        challenge.setAttempts(challenge.getAttempts() + 1);
        challenge.touchAudit(owner.actor());
        int remaining = properties.getMaxAttempts() - challenge.getAttempts();
        if (remaining <= 0) {
            challenge.setStatus(SignatureChallengeStatus.LOCKED);
            challenge.setCodeDigest(null);
            challengeRepository.save(challenge);
            return new Verification(challenge.getMethod(), "locked", locked());
        }
        challengeRepository.save(challenge);
        return new Verification(
            challenge.getMethod(),
            "invalid",
            SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "Wrong confirmation code", "signature.challenge.invalidCode").with("remainingAttempts", remaining)
        );
    }

    /**
     * The error of a challenge that cannot be used any more; an expired one is marked as such.
     */
    private RequestAlertException closedError(SignatureChallenge challenge, ZonedDateTime now) {
        return switch (challenge.getStatus()) {
            case LOCKED -> locked();
            case EXPIRED -> expired();
            case CONFIRMED, CANCELLED -> SignatureSupport.error(HttpStatus.CONFLICT, "The challenge is already closed", "signature.challenge.closed");
            case OPEN -> {
                if (!now.isAfter(challenge.getExpiresAt())) yield null;
                challenge.setStatus(SignatureChallengeStatus.EXPIRED);
                challenge.setCodeDigest(null);
                challenge.touchAudit(Owner.of(challenge).actor());
                challengeRepository.save(challenge);
                yield expired();
            }
        };
    }

    private void cancel(long id, String actor) {
        challengeRepository.findForUpdate(id).ifPresent(challenge -> {
            if (challenge.getStatus() != SignatureChallengeStatus.OPEN) return;
            challenge.setStatus(SignatureChallengeStatus.CANCELLED);
            challenge.setCodeDigest(null);
            challenge.touchAudit(actor);
            challengeRepository.save(challenge);
        });
    }

    private String newCode(SignatureChallenge challenge, ZonedDateTime now) {
        String code = codes.generate();
        challenge.setCodeDigest(codes.digest(binding(challenge), code));
        challenge.setSends(challenge.getSends() + 1);
        challenge.setLastSentAt(now);
        return code;
    }

    private void send(Created created, String tenant, Owner owner) {
        SignatureChallenge challenge = created.challenge();
        try {
            String tenantName = tenantsService.findByCode(tenant, null).map(TenantsDTO::getName).filter(name -> !name.isBlank()).orElse(tenant);
            mailService.sendCode(new SignatureMailService.CodeMessage(
                challenge.getDestinationEmail(),
                tenantName,
                created.prepared().operation(),
                created.prepared().reference(),
                created.code(),
                challenge.getExpiresAt(),
                tenantTimeZoneService.currentZoneId()
            ));
        } catch (MailException | IllegalStateException exception) {
            LOG.error("signature_mail_failed challengeId={}", challenge.getId(), exception);
            transactions.executeWithoutResult(status -> cancel(challenge.getId(), owner.actor()));
            throw SignatureSupport.error(HttpStatus.BAD_GATEWAY, "The confirmation code could not be sent", "signature.mail.sendFailed");
        }
    }

    /**
     * Limits the codes a user, or a link, can request in an hour, to protect the mailbox of the association.
     */
    private void requireHourlyLimit(Owner owner, ZonedDateTime now) {
        ZonedDateTime since = now.minusHours(1);
        List<ZonedDateTime> recent = owner.userId() != null
            ? challengeRepository.findRecentCreations(owner.userId(), since)
            : challengeRepository.findRecentLinkCreations(owner.linkId(), since);
        if (recent.size() < properties.getMaxChallengesPerHour()) return;
        ZonedDateTime available = recent.get(0).plusHours(1);
        throw SignatureSupport.error(HttpStatus.TOO_MANY_REQUESTS, "Too many codes requested in the last hour", "signature.challenge.resendTooSoon")
            .with("retryAfterSeconds", Math.max(1, Duration.between(now, available).toSeconds() + 1));
    }

    /**
     * The authentication app must be offered by the installation, configured by the user and reachable now.
     */
    private void requireTotp(String userId) {
        KeycloakOtpClient.Status status = otpClient.configured() ? otpClient.status(userId) : KeycloakOtpClient.Status.NOT_CONFIGURED;
        if (status == KeycloakOtpClient.Status.NOT_CONFIGURED) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "The authentication app is not configured", "signature.method.totpNotConfigured");
        }
        if (status == KeycloakOtpClient.Status.UNAVAILABLE) {
            metrics.challenge(SignatureMethod.TOTP, "unavailable");
            throw unavailable();
        }
    }

    private Methods methods(String userId, KeycloakOtpClient.Status totp) {
        String email = usersRepository.findByKeycloakIdAndDeletedFalse(userId).map(Users::getEmail).orElse(null);
        SignatureMethod preference = preferencesRepository.findFirstByUserIdAndKeyAndDeletedFalseOrderByIdDesc(userId, METHOD_PREFERENCE)
            .map(Preferences::getValue)
            .filter("TOTP"::equals)
            .map(value -> SignatureMethod.TOTP)
            .orElse(SignatureMethod.EMAIL_OTP);
        boolean configured = totp == KeycloakOtpClient.Status.CONFIGURED;
        return new Methods(
            inventoryService.electronicSignatureEnabled(),
            preference,
            preference == SignatureMethod.TOTP && configured ? SignatureMethod.TOTP : SignatureMethod.EMAIL_OTP,
            totp != KeycloakOtpClient.Status.UNAVAILABLE,
            configured,
            SignatureSupport.maskEmail(email)
        );
    }

    private void requireRequirements() {
        Requirements requirements = requirementsService.requirements();
        if (!requirements.satisfied()) {
            throw SignatureSupport.error(HttpStatus.UNPROCESSABLE_ENTITY, "Electronic signature requirements are missing", "signature.settings.requirementsMissing")
                .with("missing", requirements.missing());
        }
    }

    private SignaturePurposeHandler handler(SignaturePurpose purpose) {
        SignaturePurposeHandler handler = handlers.get(purpose);
        if (handler == null) {
            throw SignatureSupport.error(HttpStatus.BAD_REQUEST, "Unsupported signature purpose " + purpose, "signature.challenge.invalid");
        }
        return handler;
    }

    private SignatureChallenge owned(long id, Owner owner) {
        return challengeRepository.findForUpdate(id)
            .filter(owner::owns)
            .orElseThrow(() -> SignatureSupport.error(HttpStatus.NOT_FOUND, "Challenge not found", "signature.challenge.notFound"));
    }

    private Challenge toDto(SignatureChallenge challenge, SignaturePurposeHandler.Prepared prepared) {
        boolean email = challenge.getMethod() == SignatureMethod.EMAIL_OTP;
        return new Challenge(
            challenge.getId(),
            challenge.getPurpose(),
            challenge.getMethod(),
            SignatureSupport.maskEmail(challenge.getDestinationEmail()),
            prepared.reference(),
            challenge.getExpiresAt(),
            challenge.getLastSentAt() == null ? null : challenge.getLastSentAt().plus(properties.getResendInterval()),
            email ? Math.max(0, properties.getMaxSends() - challenge.getSends()) : 0,
            Math.max(0, properties.getMaxAttempts() - challenge.getAttempts()),
            prepared.declarations()
        );
    }

    /**
     * What a code is bound to: the challenge, its owner, the purpose and the hash of the items.
     */
    private static SignatureCodes.Binding binding(SignatureChallenge challenge) {
        String subject = challenge.getUserKeycloakId() != null ? challenge.getUserKeycloakId() : "link:" + challenge.getLinkId();
        return new SignatureCodes.Binding(challenge.getId(), subject, challenge.getPurpose().name(), challenge.getItemsHash());
    }

    private static RequestAlertException unavailable() {
        return SignatureSupport.error(HttpStatus.BAD_GATEWAY, "The authentication app cannot be verified now", "signature.method.unavailable");
    }

    private static RequestAlertException locked() {
        return SignatureSupport.error(HttpStatus.CONFLICT, "Too many wrong codes", "signature.challenge.locked");
    }

    private static RequestAlertException expired() {
        return SignatureSupport.error(HttpStatus.GONE, "The confirmation code has expired", "signature.challenge.expired");
    }

    private record Created(SignatureChallenge challenge, String code, SignaturePurposeHandler.Prepared prepared) {
        @Override
        public String toString() {
            return "Created[challengeId=" + challenge.getId() + "]";
        }
    }

    private record Verification(SignatureMethod method, String outcome, RequestAlertException error) {
    }

    /**
     * Who owns a challenge: an authenticated user or a signature link.
     */
    private record Owner(String userId, Long linkId) {
        static Owner user(String userId) {
            return new Owner(userId, null);
        }

        static Owner link(Long linkId) {
            return new Owner(null, linkId);
        }

        static Owner of(SignatureChallenge challenge) {
            return new Owner(challenge.getUserKeycloakId(), challenge.getLinkId());
        }

        boolean owns(SignatureChallenge challenge) {
            return userId != null ? userId.equals(challenge.getUserKeycloakId()) : linkId.equals(challenge.getLinkId());
        }

        String actor() {
            return userId != null ? userId : SignatureLinkSession.actor(linkId);
        }
    }
}
