package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.service.calendarfeed.CalendarFeedManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.*;

@RestController
@RequestMapping("/api/calendar-feeds")
@RequiresTenantFeature(TenantFeature.EXTERNAL_CALENDAR_FEED)
public class CalendarFeedResource {
    private final CalendarFeedManagementService service;

    public CalendarFeedResource(CalendarFeedManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<Feed> list(AbstractAuthenticationToken auth) {
        return service.listPersonal(auth);
    }

    @PostMapping
    public ResponseEntity<SecretFeed> create(@Valid @RequestBody CreateRequest request, AbstractAuthenticationToken auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPersonal(request, auth));
    }

    @PostMapping("/{id}/rotate")
    public SecretFeed rotate(@PathVariable UUID id, @Valid @RequestBody RotateRequest request, AbstractAuthenticationToken auth) {
        return service.rotate(id, request, false, auth);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, AbstractAuthenticationToken auth) {
        service.revoke(id, false, auth);
    }

    @DeleteMapping("/{id}/record")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRevoked(@PathVariable UUID id, AbstractAuthenticationToken auth) {
        service.deleteRevoked(id, false, auth);
    }
}
