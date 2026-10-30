package com.fundaro.zodiac.taurus.service.calendarfeed;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.repository.calendarfeed.*;

import java.time.Instant;

import org.springframework.stereotype.Service;

/**
 * Revokes the personal feeds of an owner who may no longer publish them.
 *
 * <p>Every download already rechecks the owner, so a feed left active would answer {@code 404}
 * anyway. Revoking it when the change happens — a role removed, an account deactivated, an erasure
 * — makes that visible in the list, writes the reason in the history and closes the global row,
 * instead of leaving a credential that looks valid until someone tries it.
 */
@Service
public class CalendarFeedOwnerRevocationService {
    private final CalendarFeedSubscriptionRepository subscriptions;
    private final CalendarFeedTokenRegistryRepository registry;
    private final CalendarFeedAuditService audit;

    public CalendarFeedOwnerRevocationService(CalendarFeedSubscriptionRepository subscriptions,
                                              CalendarFeedTokenRegistryRepository registry, CalendarFeedAuditService audit) {
        this.subscriptions = subscriptions;
        this.registry = registry;
        this.audit = audit;
    }

    /** After a change to the user: keeps the feeds whose scope the current roles still allow. */
    public void revokeUnauthorized(Users owner, String actor) {
        revoke(owner, actor, false);
    }

    /** The account is gone — deleted, deactivated or erased: no personal feed of it survives. */
    public void revokeAll(Users owner, String actor) {
        revoke(owner, actor, true);
    }

    private void revoke(Users owner, String actor, boolean all) {
        for (CalendarFeedSubscription feed : subscriptions.findAllByOwner_IdAndStatusAndDeletedFalse(owner.getId(), CalendarFeedStatus.ACTIVE)) {
            boolean authorized = !all && Boolean.TRUE.equals(owner.getActive()) && !Boolean.TRUE.equals(owner.getDeleted()) &&
                (feed.getVisibilityScope() == CalendarFeedScope.INTERNAL ? owner.getRoles().contains(RoleEnum.ROLE_USER) : owner.getRoles().contains(RoleEnum.ROLE_USER_EXTERNAL));
            if (!authorized) {
                Instant now = Instant.now();
                feed.setStatus(CalendarFeedStatus.REVOKED);
                feed.setEditDate(now);
                feed.setEditBy(actor);
                registry.revokeActive(feed.getId(), now);
                subscriptions.save(feed);
                audit.record(feed, CalendarFeedAuditAction.REVOKED, CalendarFeedAuditSource.OWNER_CHANGED, actor,
                    all ? "owner_removed" : "owner_not_authorized", now);
            }
        }
    }
}
