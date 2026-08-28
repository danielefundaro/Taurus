package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.EventPresentUserDTO;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.List;

/**
 * Service Interface for managing {@link CalendarEvents}.
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
     * Replace the present users list for an event.
     *
     * @param eventId      the id of the event.
     * @param presentUsers the list of present users.
     * @param token        the authentication token of the calling user.
     * @return the updated event DTO.
     */
    CalendarEventsDTO setPresentUsers(Long eventId, List<EventPresentUserDTO> presentUsers, AbstractAuthenticationToken token);
}
