package com.fundaro.zodiac.taurus.repository.calendarfeed;

import com.fundaro.zodiac.taurus.domain.calendarfeed.CalendarFeedIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarFeedIdempotencyRepository extends JpaRepository<CalendarFeedIdempotency, byte[]> {
    /** The retention of the receipts: a retry arrives within minutes, never after weeks. */
    int deleteByCreatedAtBefore(java.time.Instant threshold);
}
