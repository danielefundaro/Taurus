package com.fundaro.zodiac.taurus.service.calendarfeed;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.repository.calendarfeed.*;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class CalendarFeedOwnerRevocationService {
    private final CalendarFeedSubscriptionRepository subscriptions;
    private final CalendarFeedTokenRegistryRepository registry;
    public CalendarFeedOwnerRevocationService(CalendarFeedSubscriptionRepository subscriptions, CalendarFeedTokenRegistryRepository registry) { this.subscriptions = subscriptions; this.registry = registry; }
    public void revokeUnauthorized(Users owner, String actor) {
        for (CalendarFeedSubscription feed : subscriptions.findAllByOwner_IdAndStatus(owner.getId(), CalendarFeedStatus.ACTIVE)) {
            boolean authorized = Boolean.TRUE.equals(owner.getActive()) && !Boolean.TRUE.equals(owner.getDeleted()) &&
                (feed.getVisibilityScope() == CalendarFeedScope.INTERNAL ? owner.getRoles().contains(RoleEnum.ROLE_USER) : owner.getRoles().contains(RoleEnum.ROLE_USER_EXTERNAL));
            if (!authorized) {
                Instant now = Instant.now(); feed.setStatus(CalendarFeedStatus.REVOKED); feed.setUpdatedAt(now); feed.setUpdatedBy(actor);
                registry.revokeActive(feed.getId(), now); subscriptions.save(feed);
            }
        }
    }
}
