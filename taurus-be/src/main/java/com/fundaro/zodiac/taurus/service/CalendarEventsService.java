package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;

/**
 * Service Interface for managing {@link CalendarEvents}.
 */
public interface CalendarEventsService extends CommonOpenSearchService<CalendarEvents, CalendarEventsDTO, CalendarEventsCriteria> {
}
