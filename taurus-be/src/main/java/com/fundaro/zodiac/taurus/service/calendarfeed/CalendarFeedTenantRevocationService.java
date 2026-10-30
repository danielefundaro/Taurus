package com.fundaro.zodiac.taurus.service.calendarfeed;

import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.calendarfeed.*;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes every feed of a tenant that stops being served: deactivated, soft deleted or erased.
 *
 * <p>The global row is closed first, because it is what the anonymous download resolves; the
 * subscriptions of the schema follow, so the list of a tenant that comes back shows revoked feeds
 * instead of credentials that quietly resume. A schema already dropped is not an error here: the
 * global rows are the part that must not survive.
 */
@Service
@Transactional
public class CalendarFeedTenantRevocationService {
    private static final Logger LOG = LoggerFactory.getLogger(CalendarFeedTenantRevocationService.class);

    private final CalendarFeedTokenRegistryRepository registry;
    private final CalendarFeedSubscriptionRepository subscriptions;
    private final CalendarFeedAuditService audit;
    private final TenantTransactionExecutor transactions;

    public CalendarFeedTenantRevocationService(CalendarFeedTokenRegistryRepository registry,
                                               CalendarFeedSubscriptionRepository subscriptions,
                                               CalendarFeedAuditService audit, TenantTransactionExecutor transactions) {
        this.registry = registry;
        this.subscriptions = subscriptions;
        this.audit = audit;
        this.transactions = transactions;
    }

    public void revokeAll(Long tenantId, String tenantCode, String actor, String reason) {
        Instant now = Instant.now();
        int closed = registry.revokeAllActiveOfTenant(tenantId, now);
        int feeds = 0;
        try {
            feeds = transactions.execute(tenantCode, () -> {
                List<CalendarFeedSubscription> active = subscriptions.findAllByStatusAndDeletedFalse(CalendarFeedStatus.ACTIVE);
                for (CalendarFeedSubscription feed : active) {
                    feed.setStatus(CalendarFeedStatus.REVOKED);
                    feed.setEditDate(now);
                    feed.setEditBy(actor);
                    subscriptions.save(feed);
                    audit.record(feed, CalendarFeedAuditAction.REVOKED, CalendarFeedAuditSource.TENANT_DISABLED, actor, reason, now);
                }
                return active.size();
            });
        } catch (RuntimeException exception) {
            LOG.warn("calendar_feed_tenant_revocation tenantId={} outcome=schema_unavailable errorClass={}",
                tenantId, exception.getClass().getSimpleName());
        }
        LOG.info("calendar_feed_tenant_revocation tenantId={} reason={} globalRows={} feeds={}", tenantId, reason, closed, feeds);
    }
}
