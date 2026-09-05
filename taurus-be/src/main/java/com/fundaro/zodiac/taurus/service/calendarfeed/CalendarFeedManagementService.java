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
    private final UsersRepository users;
    private final TenantsRepository tenants;
    private final CalendarFeedTokenService tokens;
    private final ApplicationProperties.CalendarFeedProperties properties;

    public CalendarFeedManagementService(CalendarFeedSubscriptionRepository subscriptions,
        CalendarFeedTokenRegistryRepository registry, UsersRepository users, TenantsRepository tenants,
        CalendarFeedTokenService tokens, ApplicationProperties properties) {
        this.subscriptions = subscriptions; this.registry = registry; this.users = users; this.tenants = tenants;
        this.tokens = tokens; this.properties = properties.getCalendarFeed();
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
        Users owner = currentUser(auth);
        CalendarFeedScope scope = allowedPersonalScope(owner);
        if (subscriptions.countByOwner_IdAndStatus(owner.getId(), CalendarFeedStatus.ACTIVE) >= 3) throw conflict("Maximum active personal feeds reached");
        return create(request, CalendarFeedType.PERSONAL, scope, owner, actor(auth));
    }

    public SecretFeed createTenant(CreateRequest request, AbstractAuthenticationToken auth) {
        requireEnabled();
        if (subscriptions.countByFeedTypeAndStatus(CalendarFeedType.TENANT, CalendarFeedStatus.ACTIVE) >= 10) throw conflict("Maximum active tenant feeds reached");
        CalendarFeedScope scope = request.visibilityScope() == null ? CalendarFeedScope.INTERNAL : request.visibilityScope();
        return create(request, CalendarFeedType.TENANT, scope, null, actor(auth));
    }

    public SecretFeed rotate(UUID id, boolean admin, AbstractAuthenticationToken auth) {
        CalendarFeedSubscription subscription = subscriptions.findByIdForUpdate(id).orElseThrow(this::notFound);
        Users caller = admin ? null : currentUser(auth);
        if (subscription.getStatus() != CalendarFeedStatus.ACTIVE || subscription.getFeedType() == CalendarFeedType.PERSONAL &&
            (admin || !Objects.equals(subscription.getOwner().getId(), caller.getId()))) throw notFound();
        Instant now = Instant.now();
        registry.revokeActive(id, now);
        CalendarFeedTokenService.Token token = tokens.generate();
        subscription.setTokenVersion(subscription.getTokenVersion() + 1);
        subscription.setTokenFingerprint(tokens.fingerprint(token.digest()));
        subscription.setUpdatedAt(now); subscription.setUpdatedBy(actor(auth));
        subscriptions.save(subscription);
        saveRegistry(subscription, token.digest(), now);
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

    private SecretFeed create(CreateRequest request, CalendarFeedType type, CalendarFeedScope scope, Users owner, String actor) {
        Instant now = Instant.now(); CalendarFeedTokenService.Token token = tokens.generate();
        CalendarFeedSubscription s = new CalendarFeedSubscription();
        s.setId(UUID.randomUUID()); s.setName(request.name().trim()); s.setFeedType(type); s.setOwner(owner); s.setVisibilityScope(scope);
        s.setDetailLevel(request.detailLevel() == null ? CalendarFeedDetailLevel.MINIMAL : request.detailLevel());
        s.setPastDays(request.pastDays() == null ? properties.getDefaultPastDays() : request.pastDays());
        s.setFutureMonths(request.futureMonths() == null ? properties.getDefaultFutureMonths() : request.futureMonths());
        s.setStatus(CalendarFeedStatus.ACTIVE); s.setTokenVersion(1); s.setTokenFingerprint(tokens.fingerprint(token.digest()));
        s.setCreatedAt(now); s.setUpdatedAt(now); s.setCreatedBy(actor); s.setUpdatedBy(actor); subscriptions.save(s);
        saveRegistry(s, token.digest(), now); return secret(s, token.value());
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
