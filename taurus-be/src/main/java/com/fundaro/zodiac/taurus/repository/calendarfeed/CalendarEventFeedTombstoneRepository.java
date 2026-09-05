package com.fundaro.zodiac.taurus.repository.calendarfeed;

import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CalendarEventFeedTombstoneRepository extends JpaRepository<CalendarEventFeedTombstone, Long> {
    List<CalendarEventFeedTombstone> findByAudienceAndExpiresAtAfterAndOriginalEndDateGreaterThanEqualAndOriginalStartDateLessThanEqualOrderByOriginalStartDate(
        CalendarFeedAudience audience, Instant now, Instant from, Instant to
    );
    Optional<CalendarEventFeedTombstone> findByEventUidAndAudience(UUID eventUid, CalendarFeedAudience audience);
    void deleteByEventUidAndAudience(UUID eventUid, CalendarFeedAudience audience);
    long deleteByExpiresAtBefore(Instant now);
}
