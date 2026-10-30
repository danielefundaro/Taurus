package com.fundaro.zodiac.taurus.repository.calendarfeed;

import com.fundaro.zodiac.taurus.domain.calendarfeed.CalendarFeedAuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CalendarFeedAuditRepository extends JpaRepository<CalendarFeedAuditEntry, Long> {
    List<CalendarFeedAuditEntry> findAllBySubscriptionIdOrderByOccurredAtDescIdDesc(UUID subscriptionId);
}
