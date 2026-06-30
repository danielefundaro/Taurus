package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.service.CalendarEventsService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link CalendarEvents}.
 */
@RestController
@RequestMapping("/api/calendar-events")
public class CalendarEventsResource extends CommonOpenSearchResource<CalendarEvents, CalendarEventsDTO, CalendarEventsCriteria, CalendarEventsService> {

    public CalendarEventsResource(CalendarEventsService service) {
        super(service, CalendarEvents.class.getSimpleName(), CalendarEventsResource.class);
    }
}
