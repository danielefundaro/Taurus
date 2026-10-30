package com.fundaro.zodiac.taurus.service.calendarfeed;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.multitenancy.*;
import com.fundaro.zodiac.taurus.repository.calendarfeed.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The nightly maintenance of the feeds: expired tombstones, old idempotency receipts and the
 * consistency check between the global registry and the tenant schemas.
 *
 * <p>A tombstone outlives its ninety days for nobody: the clients that had to see the cancellation
 * have long polled. A receipt answers a retry that arrives within minutes, so keeping it for weeks
 * only keeps an encrypted token alive. An active global row whose subscription is gone, revoked or
 * on another token version is a credential nobody can see in the interface any more: the check
 * closes it and counts it, because its cause is a bug or an interrupted transaction.
 */
@Component
public class CalendarFeedTombstoneScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(CalendarFeedTombstoneScheduler.class);

    private final TenantSchemaRegistry schemas;
    private final TenantTransactionExecutor transactions;
    private final CalendarEventFeedTombstoneRepository repository;
    private final CalendarFeedIdempotencyRepository receipts;
    private final CalendarFeedSubscriptionRepository subscriptions;
    private final CalendarFeedTokenRegistryRepository registry;
    private final CalendarFeedMetrics metrics;
    private final ApplicationProperties.CalendarFeedProperties properties;

    public CalendarFeedTombstoneScheduler(TenantSchemaRegistry schemas, TenantTransactionExecutor transactions,
                                          CalendarEventFeedTombstoneRepository repository, CalendarFeedIdempotencyRepository receipts,
                                          CalendarFeedSubscriptionRepository subscriptions, CalendarFeedTokenRegistryRepository registry,
                                          CalendarFeedMetrics metrics, ApplicationProperties properties) {
        this.schemas = schemas;
        this.transactions = transactions;
        this.repository = repository;
        this.receipts = receipts;
        this.subscriptions = subscriptions;
        this.registry = registry;
        this.metrics = metrics;
        this.properties = properties.getCalendarFeed();
    }

    @Scheduled(cron = "${application.calendar-feed.cleanup-cron:0 15 3 * * *}")
    public void cleanup() {
        Instant now = Instant.now();
        Instant receiptThreshold = now.minus(properties.getIdempotencyRetentionDays(), ChronoUnit.DAYS);
        for (String tenant : schemas.findActiveTenantCodes())
            transactions.execute(tenant, () -> {
                metrics.tombstones("deleted", repository.deleteByExpiresAtBefore(now));
                metrics.receiptsDeleted(receipts.deleteByCreatedAtBefore(receiptThreshold));
            });
        checkRegistryConsistency();
    }

    /** One pass per tenant over the active global rows, with what the tenant schema says of them. */
    private void checkRegistryConsistency() {
        Map<Long, List<CalendarFeedTokenRegistry>> byTenant = transactions.execute(null, () ->
            registry.findAllByStatus(CalendarFeedStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(CalendarFeedTokenRegistry::getTenantId)));
        for (Map.Entry<Long, List<CalendarFeedTokenRegistry>> entry : byTenant.entrySet()) {
            String tenantCode = schemas.findActiveTenantCode(entry.getKey()).orElse(null);
            // A tenant that is not served resolves nothing: its rows are closed by the deactivation,
            // and an unreachable schema here would only produce a false orphan.
            if (tenantCode == null) continue;
            try {
                List<UUID> orphans = transactions.execute(tenantCode, () -> orphansOf(entry.getValue()));
                if (orphans.isEmpty()) continue;
                Instant now = Instant.now();
                transactions.execute(null, () -> orphans.forEach(id -> registry.revokeActive(id, now)));
                metrics.registryOrphans("revoked", orphans.size());
                LOG.warn("calendar_feed_registry_check tenantId={} orphans={} outcome=revoked", entry.getKey(), orphans.size());
            } catch (RuntimeException exception) {
                metrics.registryOrphans("unchecked", entry.getValue().size());
                LOG.warn("calendar_feed_registry_check tenantId={} outcome=failed errorClass={}", entry.getKey(), exception.getClass().getSimpleName());
            }
        }
    }

    private List<UUID> orphansOf(List<CalendarFeedTokenRegistry> rows) {
        Map<UUID, CalendarFeedSubscription> known = subscriptions
            .findAllByIdIn(rows.stream().map(CalendarFeedTokenRegistry::getSubscriptionId).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(CalendarFeedSubscription::getId, subscription -> subscription));
        List<UUID> orphans = new ArrayList<>();
        for (CalendarFeedTokenRegistry row : rows) {
            CalendarFeedSubscription subscription = known.get(row.getSubscriptionId());
            if (subscription == null || subscription.isDeleted()
                || subscription.getStatus() != CalendarFeedStatus.ACTIVE || subscription.getTokenVersion() != row.getTokenVersion())
                orphans.add(row.getSubscriptionId());
        }
        return orphans;
    }
}
