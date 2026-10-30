package com.fundaro.zodiac.taurus.service.calendarfeed;

import static com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.*;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.domain.enumeration.*;
import com.fundaro.zodiac.taurus.multitenancy.*;
import com.fundaro.zodiac.taurus.repository.calendarfeed.*;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CalendarFeedTokenResolver {
    private static final Logger LOG = LoggerFactory.getLogger(CalendarFeedTokenResolver.class);

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
    private final TenantFeatureService tenantFeatureService;
    private final CalendarFeedMetrics metrics;

    public CalendarFeedTokenResolver(CalendarFeedTokenService tokenService, CalendarFeedTokenRegistryRepository registry,
                                     TenantSchemaRegistry schemas, TenantTransactionExecutor transactions, CalendarFeedSubscriptionRepository subscriptions,
                                     CalendarFeedEventRepository events, CalendarEventFeedTombstoneRepository tombstones, IcalendarRenderer renderer,
                                     CalendarFeedRateLimiter rateLimiter, ApplicationProperties properties, TenantFeatureService tenantFeatureService,
                                     CalendarFeedMetrics metrics) {
        this.tokenService = tokenService;
        this.registry = registry;
        this.schemas = schemas;
        this.transactions = transactions;
        this.subscriptions = subscriptions;
        this.events = events;
        this.tombstones = tombstones;
        this.renderer = renderer;
        this.rateLimiter = rateLimiter;
        this.properties = properties.getCalendarFeed();
        this.tenantFeatureService = tenantFeatureService;
        this.metrics = metrics;
    }

    public Optional<Download> resolve(String token, String remoteAddress) {
        long startedAt = System.nanoTime();
        if (!properties.isEnabled()) return Optional.empty();
        if (!rateLimiter.allowRequest(remoteAddress)) {
            metrics.rateLimited("address");
            throw new CalendarFeedRateLimitException();
        }
        byte[] digest = tokenService.decodeAndDigest(token);
        if (digest == null) return rejected("malformed", startedAt);
        if (!rateLimiter.allowToken(digest)) {
            metrics.rateLimited("token");
            throw new CalendarFeedRateLimitException();
        }
        CalendarFeedTokenRegistry route = TenantContext.call(null, () -> registry.resolveActive(digest).orElse(null));
        if (route == null) return rejected("unknown", startedAt);
        if (!tenantFeatureService.isEnabledForTenant(route.getTenantId(), TenantFeature.EXTERNAL_CALENDAR_FEED))
            return rejected("feature", startedAt);
        String tenantCode = schemas.findActiveTenantCode(route.getTenantId()).orElse(null);
        if (tenantCode == null) return rejected("tenant", startedAt);
        Optional<Download> download = transactions.execute(tenantCode, () -> generate(route, digest));
        metrics.resolution(download.isPresent() ? "found" : "rejected", millis(startedAt));
        download.ifPresent(result -> touch(tenantCode, result.touchId()));
        return download;
    }

    private Optional<Download> generate(CalendarFeedTokenRegistry route, byte[] digest) {
        CalendarFeedSubscription subscription = subscriptions.findByIdAndDeletedFalse(route.getSubscriptionId()).orElse(null);
        if (!consistent(subscription, route, digest) || !authorized(subscription)) return Optional.empty();
        long startedAt = System.nanoTime();
        Instant now = Instant.now();
        Instant from = now.minus(subscription.getPastDays(), ChronoUnit.DAYS);
        Instant to = ZonedDateTime.ofInstant(now, ZoneOffset.UTC).plusMonths(subscription.getFutureMonths()).toInstant();
        Collection<StateEnum> states = subscription.getVisibilityScope() == CalendarFeedScope.INTERNAL
            ? List.of(StateEnum.COMPLETE, StateEnum.PUBLIC) : List.of(StateEnum.PUBLIC);
        List<CalendarEventFeedProjection> active = events.findVisible(states, Date.from(from), Date.from(to));
        CalendarFeedAudience audience = subscription.getVisibilityScope() == CalendarFeedScope.INTERNAL ? CalendarFeedAudience.INTERNAL : CalendarFeedAudience.EXTERNAL;
        List<CalendarEventFeedTombstone> cancelled = tombstones.findByAudienceAndExpiresAtAfterAndOriginalEndDateGreaterThanEqualAndOriginalStartDateLessThanEqualOrderByOriginalStartDate(audience, now, from, to);
        if (active.size() + cancelled.size() > properties.getMaxComponents()) {
            metrics.generationError("limit");
            throw new CalendarFeedUnavailableException();
        }
        Set<UUID> activeUids = new HashSet<>();
        List<RenderEvent> projected = new ArrayList<>();
        Instant modified = subscription.getEditDate();
        String base = properties.getPublicBaseUrl().replaceAll("/+$", "");
        for (CalendarEventFeedProjection e : active) {
            activeUids.add(e.getUid());
            Instant eventModified = e.getModifiedAt().toInstant();
            if (eventModified.isAfter(modified)) modified = eventModified;
            // The link travels on the same UID the file already carries: no database id leaves the schema.
            projected.add(new RenderEvent(e.getUid(), e.getSequence(), eventModified, e.getStartAt().toInstant(), e.getEndAt().toInstant(), e.getSummary(), e.getLocation(), e.getDescription(), base + "/calendar/uid/" + e.getUid(), false));
        }
        for (CalendarEventFeedTombstone t : cancelled)
            if (!activeUids.contains(t.getEventUid())) {
                if (t.getCancelledAt().isAfter(modified)) modified = t.getCancelledAt();
                projected.add(new RenderEvent(t.getEventUid(), t.getSequence(), t.getCancelledAt(), t.getOriginalStartDate(), t.getOriginalEndDate(), t.getSummarySnapshot(), null, null, null, true));
            }
        projected.sort(Comparator.comparing(RenderEvent::startAt).thenComparing(RenderEvent::uid));
        byte[] body;
        try {
            body = renderer.render(new RenderCalendar(subscription.getName(), subscription.getDetailLevel(), projected));
        } catch (RuntimeException exception) {
            metrics.generationError("render");
            throw exception;
        }
        metrics.generation(millis(startedAt), projected.size());
        String etag = "\"" + sha256Hex(body) + "\"";
        UUID touchId = stale(subscription.getLastAccessedAt(), now) ? subscription.getId() : null;
        return Optional.of(new Download(body, etag, modified.truncatedTo(ChronoUnit.SECONDS),
            subscription.getFeedType().name(), subscription.getVisibilityScope().name(), touchId));
    }

    /**
     * The approximate last access, written outside the transaction that produced the calendar.
     *
     * <p>It is informative — the access may be the provider's, not the subscriber's — so a failed
     * write must not turn a correct ICS response into a {@code 503}: the failure is logged and the
     * next download, a day later at the earliest, writes it again.
     */
    private void touch(String tenantCode, UUID subscriptionId) {
        if (subscriptionId == null) return;
        Instant now = Instant.now();
        try {
            transactions.execute(tenantCode, () -> {
                subscriptions.findByIdAndDeletedFalse(subscriptionId).ifPresent(subscription -> {
                    if (stale(subscription.getLastAccessedAt(), now)) {
                        subscription.setLastAccessedAt(now);
                        subscriptions.save(subscription);
                    }
                });
                return null;
            });
        } catch (RuntimeException exception) {
            LOG.warn("calendar_feed_last_access outcome=failed errorClass={}", exception.getClass().getSimpleName());
        }
    }

    /** At most one write a day: a subscribed client polls far more often than that. */
    private static boolean stale(Instant lastAccessedAt, Instant now) {
        return lastAccessedAt == null || lastAccessedAt.isBefore(now.minus(1, ChronoUnit.DAYS));
    }

    private Optional<Download> rejected(String reason, long startedAt) {
        metrics.resolution(reason, millis(startedAt));
        return Optional.empty();
    }

    private static long millis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private boolean consistent(CalendarFeedSubscription s, CalendarFeedTokenRegistry r, byte[] digest) {
        return s != null && s.getStatus() == CalendarFeedStatus.ACTIVE && s.getId().equals(r.getSubscriptionId()) &&
            s.getTokenVersion() == r.getTokenVersion() && MessageDigest.isEqual(s.getTokenFingerprint().getBytes(StandardCharsets.US_ASCII), tokenService.fingerprint(digest).getBytes(StandardCharsets.US_ASCII));
    }

    private boolean authorized(CalendarFeedSubscription s) {
        if (s.getFeedType() == CalendarFeedType.TENANT) return true;
        var owner = s.getOwner();
        if (owner == null || Boolean.TRUE.equals(owner.getDeleted()) || !Boolean.TRUE.equals(owner.getActive()))
            return false;
        return s.getVisibilityScope() == CalendarFeedScope.INTERNAL ? owner.getRoles().contains(RoleEnum.ROLE_USER) : owner.getRoles().contains(RoleEnum.ROLE_USER_EXTERNAL);
    }

    private static String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
