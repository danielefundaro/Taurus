package com.fundaro.zodiac.taurus.service.calendarfeed;

import static com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.*;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.domain.enumeration.*;
import com.fundaro.zodiac.taurus.multitenancy.*;
import com.fundaro.zodiac.taurus.repository.calendarfeed.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CalendarFeedTokenResolver {
    private final CalendarFeedTokenService tokenService;
    private final CalendarFeedTokenRegistryRepository registry;
    private final TenantSchemaRegistry schemas;
    private final TenantTransactionExecutor transactions;
    private final CalendarFeedSubscriptionRepository subscriptions;
    private final CalendarFeedEventRepository events;
    private final CalendarEventFeedTombstoneRepository tombstones;
    private final IcalendarRenderer renderer;
    private final CalendarFeedRateLimiter rateLimiter;
    private final ApplicationProperties.CalendarFeedProperties properties;

    public CalendarFeedTokenResolver(CalendarFeedTokenService tokenService, CalendarFeedTokenRegistryRepository registry,
        TenantSchemaRegistry schemas, TenantTransactionExecutor transactions, CalendarFeedSubscriptionRepository subscriptions,
        CalendarFeedEventRepository events, CalendarEventFeedTombstoneRepository tombstones, IcalendarRenderer renderer,
        CalendarFeedRateLimiter rateLimiter, ApplicationProperties properties) {
        this.tokenService = tokenService; this.registry = registry; this.schemas = schemas; this.transactions = transactions;
        this.subscriptions = subscriptions; this.events = events; this.tombstones = tombstones; this.renderer = renderer;
        this.rateLimiter = rateLimiter; this.properties = properties.getCalendarFeed();
    }

    public Optional<Download> resolve(String token) {
        if (!properties.isEnabled()) return Optional.empty();
        byte[] digest = tokenService.decodeAndDigest(token); if (digest == null) return Optional.empty();
        if (!rateLimiter.allow(digest)) throw new CalendarFeedRateLimitException();
        CalendarFeedTokenRegistry route = TenantContext.call(null, () -> registry.resolveActive(digest).orElse(null));
        if (route == null) return Optional.empty();
        String tenantCode = schemas.findActiveTenantCode(route.getTenantId()).orElse(null); if (tenantCode == null) return Optional.empty();
        return transactions.execute(tenantCode, () -> generate(route, digest));
    }

    private Optional<Download> generate(CalendarFeedTokenRegistry route, byte[] digest) {
        CalendarFeedSubscription subscription = subscriptions.findById(route.getSubscriptionId()).orElse(null);
        if (!consistent(subscription, route, digest) || !authorized(subscription)) return Optional.empty();
        Instant now = Instant.now(); Instant from = now.minus(subscription.getPastDays(), java.time.temporal.ChronoUnit.DAYS);
        Instant to = ZonedDateTime.ofInstant(now, ZoneOffset.UTC).plusMonths(subscription.getFutureMonths()).toInstant();
        Collection<StateEnum> states = subscription.getVisibilityScope() == CalendarFeedScope.INTERNAL
            ? List.of(StateEnum.COMPLETE, StateEnum.PUBLIC) : List.of(StateEnum.PUBLIC);
        List<CalendarEventFeedProjection> active = events.findVisible(states, Date.from(from), Date.from(to));
        CalendarFeedAudience audience = subscription.getVisibilityScope() == CalendarFeedScope.INTERNAL ? CalendarFeedAudience.INTERNAL : CalendarFeedAudience.EXTERNAL;
        List<CalendarEventFeedTombstone> cancelled = tombstones.findByAudienceAndExpiresAtAfterAndOriginalEndDateGreaterThanEqualAndOriginalStartDateLessThanEqualOrderByOriginalStartDate(audience, now, from, to);
        if (active.size() + cancelled.size() > properties.getMaxComponents()) throw new CalendarFeedUnavailableException();
        Set<UUID> activeUids = new HashSet<>(); List<RenderEvent> projected = new ArrayList<>(); Instant modified = subscription.getUpdatedAt();
        String base = properties.getPublicBaseUrl().replaceAll("/+$", "");
        for (CalendarEventFeedProjection e : active) {
            activeUids.add(e.getUid()); Instant eventModified = e.getModifiedAt().toInstant(); if (eventModified.isAfter(modified)) modified = eventModified;
            projected.add(new RenderEvent(e.getUid(), e.getSequence(), eventModified, e.getStartAt().toInstant(), e.getEndAt().toInstant(), e.getSummary(), e.getLocation(), e.getDescription(), base + "/calendar/" + e.getEventId(), false));
        }
        for (CalendarEventFeedTombstone t : cancelled) if (!activeUids.contains(t.getEventUid())) {
            if (t.getCancelledAt().isAfter(modified)) modified = t.getCancelledAt();
            projected.add(new RenderEvent(t.getEventUid(), t.getSequence(), t.getCancelledAt(), t.getOriginalStartDate(), t.getOriginalEndDate(), t.getSummarySnapshot(), null, null, null, true));
        }
        projected.sort(Comparator.comparing(RenderEvent::startAt).thenComparing(RenderEvent::uid));
        byte[] body = renderer.render(new RenderCalendar(subscription.getName(), subscription.getDetailLevel(), projected));
        String etag = '"' + sha256Hex(body) + '"';
        if (subscription.getLastAccessedAt() == null || subscription.getLastAccessedAt().isBefore(now.minus(1, java.time.temporal.ChronoUnit.DAYS))) {
            subscription.setLastAccessedAt(now); subscriptions.save(subscription);
        }
        return Optional.of(new Download(body, etag, modified.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)));
    }

    private boolean consistent(CalendarFeedSubscription s, CalendarFeedTokenRegistry r, byte[] digest) {
        return s != null && s.getStatus() == CalendarFeedStatus.ACTIVE && s.getId().equals(r.getSubscriptionId()) &&
            s.getTokenVersion() == r.getTokenVersion() && MessageDigest.isEqual(s.getTokenFingerprint().getBytes(StandardCharsets.US_ASCII), tokenService.fingerprint(digest).getBytes(StandardCharsets.US_ASCII));
    }
    private boolean authorized(CalendarFeedSubscription s) {
        if (s.getFeedType() == CalendarFeedType.TENANT) return true;
        var owner = s.getOwner(); if (owner == null || Boolean.TRUE.equals(owner.getDeleted()) || !Boolean.TRUE.equals(owner.getActive())) return false;
        return s.getVisibilityScope() == CalendarFeedScope.INTERNAL ? owner.getRoles().contains(RoleEnum.ROLE_USER) : owner.getRoles().contains(RoleEnum.ROLE_USER_EXTERNAL);
    }
    private static String sha256Hex(byte[] value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
}
