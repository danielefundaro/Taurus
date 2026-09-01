package com.fundaro.zodiac.taurus.web.rest.external;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.criteria.CalendarEventsCriteria;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.BulkAvailabilityResultDTO;
import com.fundaro.zodiac.taurus.service.user.CalendarEventsService;
import com.fundaro.zodiac.taurus.web.rest.user.CommonOpenSearchResource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller of ROLE_USER_EXTERNAL for managing {@link CalendarEvents} (PUBLIC state only).
 */
@RestController("ExternalPermissionsCalendarEventsResource")
@RequestMapping("/api/external/calendar-events")
public class CalendarEventsResource extends CommonOpenSearchResource<CalendarEvents, CalendarEventsDTO, CalendarEventsCriteria, CalendarEventsService> {

    public CalendarEventsResource(@Qualifier("ExternalPermissionsCalendarEventsService") CalendarEventsService service) {
        super(service, CalendarEvents.class.getSimpleName(), CalendarEventsResource.class);
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<CalendarEventsDTO> setAvailability(@PathVariable("id") Long id, @RequestParam boolean available, AbstractAuthenticationToken token) {
        getLog().debug("REST request to set availability for CalendarEvents : {}, available={}", id, available);
        CalendarEventsDTO result = getService().setAvailability(id, available, token);
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}/availability")
    public ResponseEntity<CalendarEventsDTO> cancelAvailability(@PathVariable("id") Long id, AbstractAuthenticationToken token) {
        getLog().debug("REST request to cancel availability for CalendarEvents : {}", id);
        CalendarEventsDTO result = getService().cancelAvailability(id, token);
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/series/{seriesId}/availability")
    public ResponseEntity<BulkAvailabilityResultDTO> setSeriesAvailability(
        @PathVariable Long seriesId,
        @RequestParam boolean available,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.ok(getService().setSeriesAvailability(seriesId, available, token));
    }

    @DeleteMapping("/series/{seriesId}/availability")
    public ResponseEntity<BulkAvailabilityResultDTO> cancelSeriesAvailability(
        @PathVariable Long seriesId,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.ok(getService().setSeriesAvailability(seriesId, null, token));
    }
}
