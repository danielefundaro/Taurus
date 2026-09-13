package com.fundaro.zodiac.taurus.repository.calendarfeed;

import com.fundaro.zodiac.taurus.domain.calendarfeed.CalendarFeedIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarFeedIdempotencyRepository extends JpaRepository<CalendarFeedIdempotency, byte[]> {
}
