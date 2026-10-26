package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.CalendarEventSeries;

import java.util.Optional;

public interface CalendarEventSeriesRepository extends CatalogRepository<CalendarEventSeries> {
    Optional<CalendarEventSeries> findByIdempotencyKey(String idempotencyKey);
}
