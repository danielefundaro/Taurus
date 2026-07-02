package com.fundaro.zodiac.taurus.web.rest.user;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.user.CalendarEventsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller of ROLE_USER for managing {@link CalendarEvents}.
 */
@RestController("LowPermissionsCalendarEventsResource")
@RequestMapping("/api/user/calendar-events")
public class CalendarEventsResource extends CommonOpenSearchResource<CalendarEvents, CalendarEventsDTO, CalendarEventsCriteria, CalendarEventsService> {

    public CalendarEventsResource(CalendarEventsService service) {
        super(service, CalendarEvents.class.getSimpleName(), CalendarEventsResource.class);
    }

    /**
     * {@code PATCH /api/user/calendar-events/{id}/availability} : set the current user's availability.
     *
     * @param id        the id of the event.
     * @param available true if the user is available, false otherwise.
     * @param token     the authentication token.
     * @return the updated {@link CalendarEventsDTO}.
     */
    @PatchMapping("/{id}/availability")
    public ResponseEntity<CalendarEventsDTO> setAvailability(@PathVariable("id") String id, @RequestParam boolean available, AbstractAuthenticationToken token) {
        getLog().debug("REST request to set availability for CalendarEvents : {}, available={}", id, available);
        CalendarEventsDTO result = getService().setAvailability(id, available, token);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}/availability")
    public ResponseEntity<CalendarEventsDTO> cancelAvailability(@PathVariable("id") String id, AbstractAuthenticationToken token) {
        getLog().debug("REST request to cancel availability for CalendarEvents : {}", id);
        CalendarEventsDTO result = getService().cancelAvailability(id, token);
        return ResponseEntity.ok(result);
    }
}
