package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.service.CalendarEventsService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.EventPresentUserDTO;
import com.fundaro.zodiac.taurus.service.dto.BulkAvailabilityResultDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing {@link CalendarEvents}.
 */
@RestController
@RequestMapping("/api/calendar-events")
public class CalendarEventsResource extends CommonOpenSearchResource<CalendarEvents, CalendarEventsDTO, CalendarEventsCriteria, CalendarEventsService> {

    public CalendarEventsResource(CalendarEventsService service) {
        super(service, CalendarEvents.class.getSimpleName(), CalendarEventsResource.class);
    }

    /**
     * {@code PATCH /api/calendar-events/{id}/availability} : set the current user's availability.
     *
     * @param id        the id of the event.
     * @param available true if the user is available, false otherwise.
     * @param token     the authentication token.
     * @return the updated {@link CalendarEventsDTO}.
     */
    @PatchMapping("/{id}/availability")
    public ResponseEntity<CalendarEventsDTO> setAvailability(@PathVariable("id") Long id, @RequestParam boolean available, AbstractAuthenticationToken token) {
        getLog().debug("REST request to set availability for CalendarEvents : {}, available={}", id, available);
        CalendarEventsDTO result = getService().setAvailability(id, available, token);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/availability/cancel")
    public ResponseEntity<CalendarEventsDTO> cancelAvailability(@PathVariable("id") Long id, AbstractAuthenticationToken token) {
        getLog().debug("REST request to cancel availability for CalendarEvents : {}", id);
        CalendarEventsDTO result = getService().cancelAvailability(id, token);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/presences")
    public ResponseEntity<CalendarEventsDTO> setPresentUsers(@PathVariable("id") Long id, @RequestBody List<EventPresentUserDTO> presentUsers, AbstractAuthenticationToken token) {
        getLog().debug("REST request to set present users for CalendarEvents : {}", id);
        CalendarEventsDTO result = getService().setPresentUsers(id, presentUsers, token);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/series/{seriesId}/availability")
    public ResponseEntity<BulkAvailabilityResultDTO> setSeriesAvailability(
        @PathVariable Long seriesId,
        @RequestParam boolean available,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.ok(getService().setSeriesAvailability(seriesId, available, null, token));
    }

    @PatchMapping("/series/{seriesId}/availability/cancel")
    public ResponseEntity<BulkAvailabilityResultDTO> cancelSeriesAvailability(
        @PathVariable Long seriesId,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.ok(getService().setSeriesAvailability(seriesId, null, null, token));
    }
}
