package com.fundaro.zodiac.taurus.repository.calendarfeed;

import com.fundaro.zodiac.taurus.domain.calendarfeed.CalendarEventFeedTombstone;
import com.fundaro.zodiac.taurus.domain.calendarfeed.CalendarFeedAudience;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarEventFeedTombstoneRepository extends JpaRepository<CalendarEventFeedTombstone, Long> {
    List<CalendarEventFeedTombstone> findByAudienceAndExpiresAtAfterAndOriginalEndDateGreaterThanEqualAndOriginalStartDateLessThanEqualOrderByOriginalStartDate(
        CalendarFeedAudience audience, Instant now, Instant from, Instant to
    );

    Optional<CalendarEventFeedTombstone> findByEventUidAndAudience(UUID eventUid, CalendarFeedAudience audience);

    void deleteByEventUidAndAudience(UUID eventUid, CalendarFeedAudience audience);

    long deleteByExpiresAtBefore(Instant now);
}
