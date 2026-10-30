package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.service.calendarfeed.CalendarFeedManagementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.*;

@RestController
@RequestMapping("/api/admin/calendar-feeds")
@RequiresTenantFeature(TenantFeature.EXTERNAL_CALENDAR_FEED)
// The HTTP rule of /api/admin/** already keeps everyone else out: the annotation is the second
// control, applied by the method security, and it travels with the class.
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
public class AdminCalendarFeedResource {
    private final CalendarFeedManagementService service;

    public AdminCalendarFeedResource(CalendarFeedManagementService service) {
        this.service = service;
    }

    @GetMapping
    public List<Feed> list() {
        return service.listAdmin();
    }

    /** The history of any feed of the tenant, personal ones included, for incident response. */
    @GetMapping("/{id}/audit")
    public List<AuditEntry> history(@PathVariable UUID id, AbstractAuthenticationToken auth) {
        return service.history(id, true, auth);
    }

    @PostMapping
    public ResponseEntity<SecretFeed> create(@Valid @RequestBody CreateRequest request, AbstractAuthenticationToken auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTenant(request, auth));
    }

    @PostMapping("/{id}/rotate")
    public SecretFeed rotate(@PathVariable UUID id, @Valid @RequestBody RotateRequest request, AbstractAuthenticationToken auth) {
        return service.rotate(id, request, true, auth);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id, AbstractAuthenticationToken auth) {
        service.revoke(id, true, auth);
    }

    @DeleteMapping("/{id}/record")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRevoked(@PathVariable UUID id, AbstractAuthenticationToken auth) {
        service.deleteRevoked(id, true, auth);
    }
}
