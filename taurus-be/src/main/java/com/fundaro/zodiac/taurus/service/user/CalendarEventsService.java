package com.fundaro.zodiac.taurus.service.user;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.BulkAvailabilityResultDTO;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Service Interface (ROLE_USER) for managing {@link CalendarEvents}.
 */
public interface CalendarEventsService extends CommonOpenSearchService<CalendarEvents, CalendarEventsDTO, CalendarEventsCriteria> {

    /**
     * Set the current user's availability for an event.
     *
     * @param eventId   the id of the event.
     * @param available true if available, false if unavailable.
     * @param token     the authentication token of the calling user.
     * @return the updated event DTO.
     */
    CalendarEventsDTO setAvailability(Long eventId, boolean available, AbstractAuthenticationToken token);

    /**
     * Cancel the current user's availability response for an event.
     *
     * @param eventId the id of the event.
     * @param token   the authentication token of the calling user.
     * @return the updated event DTO.
     */
    CalendarEventsDTO cancelAvailability(Long eventId, AbstractAuthenticationToken token);

    /**
     * Set the personal reminder of the current user for an event.
     *
     * @param minutes minutes before the event, {@code 0} to disable it and {@code null} to fall back to the event value.
     */
    CalendarEventsDTO setReminderMinutes(Long eventId, Integer minutes, AbstractAuthenticationToken token);

    /** The personal reminder of the current user, {@code null} when not customised. */
    Integer findReminderMinutes(Long eventId, AbstractAuthenticationToken token);

    BulkAvailabilityResultDTO setSeriesAvailability(Long seriesId, Boolean available, AbstractAuthenticationToken token);
}
