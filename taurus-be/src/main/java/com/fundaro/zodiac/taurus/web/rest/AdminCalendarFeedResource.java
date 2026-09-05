package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.*;
import com.fundaro.zodiac.taurus.service.calendarfeed.CalendarFeedManagementService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/calendar-feeds")
public class AdminCalendarFeedResource {
    private final CalendarFeedManagementService service;
    public AdminCalendarFeedResource(CalendarFeedManagementService service) { this.service = service; }
    @GetMapping public List<Feed> list() { return service.listAdmin(); }
    @PostMapping public ResponseEntity<SecretFeed> create(@Valid @RequestBody CreateRequest request, AbstractAuthenticationToken auth) { return ResponseEntity.status(HttpStatus.CREATED).body(service.createTenant(request, auth)); }
    @PostMapping("/{id}/rotate") public SecretFeed rotate(@PathVariable UUID id, AbstractAuthenticationToken auth) { return service.rotate(id, true, auth); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void revoke(@PathVariable UUID id, AbstractAuthenticationToken auth) { service.revoke(id, true, auth); }
}
