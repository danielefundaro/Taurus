package com.fundaro.zodiac.taurus.service.calendarfeed;

import static com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.*;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.repository.*;
import com.fundaro.zodiac.taurus.repository.calendarfeed.*;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CalendarFeedManagementService {
    private final CalendarFeedSubscriptionRepository subscriptions;
    private final CalendarFeedTokenRegistryRepository registry;
    private final CalendarFeedIdempotencyRepository idempotency;
    private final UsersRepository users;
    private final TenantsRepository tenants;
    private final CalendarFeedTokenService tokens;
    private final CalendarFeedIdempotencyCodec idempotencyCodec;
    private final EntityManager entityManager;
    private final ApplicationProperties.CalendarFeedProperties properties;

    public CalendarFeedManagementService(CalendarFeedSubscriptionRepository subscriptions,
        CalendarFeedTokenRegistryRepository registry, CalendarFeedIdempotencyRepository idempotency,
        UsersRepository users, TenantsRepository tenants, CalendarFeedTokenService tokens,
        CalendarFeedIdempotencyCodec idempotencyCodec, EntityManager entityManager, ApplicationProperties properties) {
        this.subscriptions = subscriptions; this.registry = registry; this.idempotency = idempotency;
        this.users = users; this.tenants = tenants; this.tokens = tokens; this.idempotencyCodec = idempotencyCodec;
        this.entityManager = entityManager; this.properties = properties.getCalendarFeed();
    }

    @Transactional(readOnly = true)
    public List<Feed> listPersonal(AbstractAuthenticationToken auth) {
        Users owner = currentUser(auth);
        return subscriptions.findAllByOwner_IdOrderByCreatedAtDesc(owner.getId()).stream().map(this::dto).toList();
    }

    @Transactional(readOnly = true)
    public List<Feed> listAdmin() { return subscriptions.findAllByOrderByCreatedAtDesc().stream().map(this::dto).toList(); }

    public SecretFeed createPersonal(CreateRequest request, AbstractAuthenticationToken auth) {
        requireEnabled();
        requireStrongIdempotencyKey(request.idempotencyKey());
        Users owner = currentUser(auth);
        CalendarFeedScope scope = allowedPersonalScope(owner);
        String actor = actor(auth);
        byte[] requestDigest = idempotencyCodec.digest(actor, "CREATE_PERSONAL", null, request.idempotencyKey());
        byte[] fingerprint = createFingerprint(request, CalendarFeedType.PERSONAL, scope);
        lock(requestDigest);
        SecretFeed replay = replay(requestDigest, fingerprint, request.idempotencyKey());
        if (replay != null) return replay;
        if (subscriptions.countByOwner_IdAndStatus(owner.getId(), CalendarFeedStatus.ACTIVE) >= 3) throw conflict("Maximum active personal feeds reached");
        return create(request, CalendarFeedType.PERSONAL, scope, owner, actor, requestDigest, fingerprint);
    }

    public SecretFeed createTenant(CreateRequest request, AbstractAuthenticationToken auth) {
        requireEnabled();
        requireStrongIdempotencyKey(request.idempotencyKey());
        CalendarFeedScope scope = request.visibilityScope() == null ? CalendarFeedScope.INTERNAL : request.visibilityScope();
        String actor = actor(auth);
        byte[] requestDigest = idempotencyCodec.digest(actor, "CREATE_TENANT", null, request.idempotencyKey());
        byte[] fingerprint = createFingerprint(request, CalendarFeedType.TENANT, scope);
        lock(requestDigest);
        SecretFeed replay = replay(requestDigest, fingerprint, request.idempotencyKey());
        if (replay != null) return replay;
        if (subscriptions.countByFeedTypeAndStatus(CalendarFeedType.TENANT, CalendarFeedStatus.ACTIVE) >= 10) throw conflict("Maximum active tenant feeds reached");
        return create(request, CalendarFeedType.TENANT, scope, null, actor, requestDigest, fingerprint);
    }

    public SecretFeed rotate(UUID id, RotateRequest request, boolean admin, AbstractAuthenticationToken auth) {
        requireEnabled();
        requireStrongIdempotencyKey(request.idempotencyKey());
        CalendarFeedSubscription subscription = subscriptions.findByIdForUpdate(id).orElseThrow(this::notFound);
        Users caller = admin ? null : currentUser(auth);
        if (subscription.getStatus() != CalendarFeedStatus.ACTIVE || subscription.getFeedType() == CalendarFeedType.PERSONAL &&
            (admin || !Objects.equals(subscription.getOwner().getId(), caller.getId()))) throw notFound();
        String actor = actor(auth);
        byte[] requestDigest = idempotencyCodec.digest(actor, "ROTATE", id, request.idempotencyKey());
        byte[] fingerprint = idempotencyCodec.fingerprint("ROTATE\u0000" + id);
        lock(requestDigest);
        SecretFeed replay = replay(requestDigest, fingerprint, request.idempotencyKey());
        if (replay != null) return replay;
        Instant now = Instant.now();
        registry.revokeActive(id, now);
        CalendarFeedTokenService.Token token = tokens.generate();
        subscription.setTokenVersion(subscription.getTokenVersion() + 1);
        subscription.setTokenFingerprint(tokens.fingerprint(token.digest()));
        subscription.setUpdatedAt(now); subscription.setUpdatedBy(actor);
        subscriptions.save(subscription);
        saveRegistry(subscription, token.digest(), now);
        saveIdempotency(requestDigest, fingerprint, request.idempotencyKey(), subscription, token.value(), now);
        return secret(subscription, token.value());
    }

    public void revoke(UUID id, boolean admin, AbstractAuthenticationToken auth) {
        CalendarFeedSubscription subscription = subscriptions.findByIdForUpdate(id).orElse(null);
        if (subscription == null) return;
        if (!admin) {
            Users caller = currentUser(auth);
            if (subscription.getFeedType() != CalendarFeedType.PERSONAL || !Objects.equals(subscription.getOwner().getId(), caller.getId())) throw notFound();
        }
        if (subscription.getStatus() == CalendarFeedStatus.REVOKED) return;
        Instant now = Instant.now();
        subscription.setStatus(CalendarFeedStatus.REVOKED); subscription.setUpdatedAt(now); subscription.setUpdatedBy(actor(auth));
        registry.revokeActive(id, now); subscriptions.save(subscription);
    }

    private SecretFeed create(CreateRequest request, CalendarFeedType type, CalendarFeedScope scope, Users owner, String actor,
        byte[] requestDigest, byte[] fingerprint) {
        Instant now = Instant.now(); CalendarFeedTokenService.Token token = tokens.generate();
        CalendarFeedSubscription s = new CalendarFeedSubscription();
        s.setId(UUID.randomUUID()); s.setName(request.name().trim()); s.setFeedType(type); s.setOwner(owner); s.setVisibilityScope(scope);
        s.setDetailLevel(request.detailLevel() == null ? CalendarFeedDetailLevel.MINIMAL : request.detailLevel());
        s.setPastDays(request.pastDays() == null ? properties.getDefaultPastDays() : request.pastDays());
        s.setFutureMonths(request.futureMonths() == null ? properties.getDefaultFutureMonths() : request.futureMonths());
        s.setStatus(CalendarFeedStatus.ACTIVE); s.setTokenVersion(1); s.setTokenFingerprint(tokens.fingerprint(token.digest()));
        s.setCreatedAt(now); s.setUpdatedAt(now); s.setCreatedBy(actor); s.setUpdatedBy(actor); subscriptions.save(s);
        saveRegistry(s, token.digest(), now);
        saveIdempotency(requestDigest, fingerprint, request.idempotencyKey(), s, token.value(), now);
        return secret(s, token.value());
    }

    private SecretFeed replay(byte[] requestDigest, byte[] fingerprint, UUID key) {
        CalendarFeedIdempotency receipt = idempotency.findById(requestDigest).orElse(null);
        if (receipt == null) return null;
        if (!MessageDigest.isEqual(receipt.getRequestFingerprint(), fingerprint)) throw conflict("Idempotency key already used for a different request");
        CalendarFeedSubscription subscription = subscriptions.findById(receipt.getSubscriptionId()).orElseThrow(this::notFound);
        if (subscription.getStatus() != CalendarFeedStatus.ACTIVE || subscription.getTokenVersion() != receipt.getTokenVersion()) {
            throw conflict("Idempotency response is no longer current");
        }
        String token = idempotencyCodec.decrypt(receipt.getTokenCiphertext(), receipt.getTokenNonce(), key, requestDigest);
        byte[] digest = tokens.decodeAndDigest(token);
        if (digest == null || !MessageDigest.isEqual(subscription.getTokenFingerprint().getBytes(StandardCharsets.US_ASCII),
            tokens.fingerprint(digest).getBytes(StandardCharsets.US_ASCII))) throw conflict("Idempotency response is no longer current");
        return secret(subscription, token);
    }

    private void saveIdempotency(byte[] requestDigest, byte[] fingerprint, UUID key,
        CalendarFeedSubscription subscription, String token, Instant now) {
        CalendarFeedIdempotencyCodec.EncryptedToken encrypted = idempotencyCodec.encrypt(token, key, requestDigest);
        CalendarFeedIdempotency receipt = new CalendarFeedIdempotency();
        receipt.setRequestDigest(requestDigest); receipt.setSubscriptionId(subscription.getId());
        receipt.setRequestFingerprint(fingerprint); receipt.setTokenVersion(subscription.getTokenVersion());
        receipt.setTokenCiphertext(encrypted.ciphertext()); receipt.setTokenNonce(encrypted.nonce()); receipt.setCreatedAt(now);
        idempotency.save(receipt);
    }

    private byte[] createFingerprint(CreateRequest request, CalendarFeedType type, CalendarFeedScope scope) {
        CalendarFeedDetailLevel detail = request.detailLevel() == null ? CalendarFeedDetailLevel.MINIMAL : request.detailLevel();
        int pastDays = request.pastDays() == null ? properties.getDefaultPastDays() : request.pastDays();
        int futureMonths = request.futureMonths() == null ? properties.getDefaultFutureMonths() : request.futureMonths();
        return idempotencyCodec.fingerprint(String.join("\u0000", "CREATE", type.name(), request.name().trim(), scope.name(),
            detail.name(), Integer.toString(pastDays), Integer.toString(futureMonths)));
    }

    private void lock(byte[] requestDigest) {
        entityManager.createNativeQuery("select 1 from pg_advisory_xact_lock(:lockKey)")
            .setParameter("lockKey", idempotencyCodec.lockKey(requestDigest)).getSingleResult();
    }

    private void requireStrongIdempotencyKey(UUID key) {
        if (key == null || key.version() != 4 || key.variant() != 2) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "A random UUID v4 idempotency key is required",
                "CalendarFeed", "calendarFeed.idempotencyKey");
        }
    }

    private void saveRegistry(CalendarFeedSubscription s, byte[] digest, Instant now) {
        String tenantCode = com.fundaro.zodiac.taurus.multitenancy.TenantContext.getTenantCode().orElseThrow();
        Tenants tenant = tenants.findByCodeAndDeletedFalse(tenantCode).orElseThrow(this::notFound);
        CalendarFeedTokenRegistry row = new CalendarFeedTokenRegistry(); row.setTokenDigest(digest); row.setSubscriptionId(s.getId());
        row.setTenantId(tenant.getId()); row.setTokenVersion(s.getTokenVersion()); row.setStatus(CalendarFeedStatus.ACTIVE); row.setCreatedAt(now); registry.save(row);
    }
    private Users currentUser(AbstractAuthenticationToken auth) { return users.findByKeycloakIdAndDeletedFalse(actor(auth)).filter(u -> Boolean.TRUE.equals(u.getActive())).orElseThrow(this::notFound); }
    private CalendarFeedScope allowedPersonalScope(Users owner) {
        if (owner.getRoles().contains(RoleEnum.ROLE_USER)) return CalendarFeedScope.INTERNAL;
        if (owner.getRoles().contains(RoleEnum.ROLE_USER_EXTERNAL)) return CalendarFeedScope.PUBLIC_ONLY;
        throw new RequestAlertException(HttpStatus.FORBIDDEN, "A participant role is required", "CalendarFeed", "calendarFeed.role");
    }
    private Feed dto(CalendarFeedSubscription s) { return new Feed(s.getId(), s.getName(), s.getFeedType(), s.getVisibilityScope(), s.getDetailLevel(), s.getPastDays(), s.getFutureMonths(), s.getStatus(), s.getTokenFingerprint(), s.getOwner() == null ? null : s.getOwner().getId(), s.getCreatedBy(), s.getCreatedAt(), s.getLastAccessedAt()); }
    private SecretFeed secret(CalendarFeedSubscription s, String token) { String base = properties.getPublicBaseUrl().replaceAll("/+$", ""); return new SecretFeed(s.getId(), s.getName(), s.getFeedType(), s.getVisibilityScope(), s.getDetailLevel(), s.getPastDays(), s.getFutureMonths(), base + "/api/calendar-subscriptions/v1/" + token + "/calendar.ics", true, s.getCreatedAt()); }
    private String actor(AbstractAuthenticationToken auth) { return Optional.ofNullable(SecurityUtils.getUserIdFromAuthentication(auth)).orElse("system"); }
    private void requireEnabled() { if (!properties.isEnabled()) throw notFound(); }
    private RequestAlertException notFound() { return new RequestAlertException(HttpStatus.NOT_FOUND, "Feed not found", "CalendarFeed", "calendarFeed.notFound"); }
    private RequestAlertException conflict(String msg) { return new RequestAlertException(HttpStatus.CONFLICT, msg, "CalendarFeed", "calendarFeed.limit"); }
}
