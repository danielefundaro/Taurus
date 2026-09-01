package com.fundaro.zodiac.taurus.repository;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import java.util.List;

public interface CalendarEventsRepository extends CatalogRepository<CalendarEvents> {
    List<CalendarEvents> findAllBySeries_IdOrderByOriginalStartDateAsc(Long seriesId);
}
