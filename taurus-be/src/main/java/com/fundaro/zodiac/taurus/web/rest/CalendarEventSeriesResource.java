package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.CalendarEventSeriesService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventSeriesDTO;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventSeriesPreviewDTO;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventSeriesRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendar-event-series")
public class CalendarEventSeriesResource {

    private final CalendarEventSeriesService service;

    public CalendarEventSeriesResource(CalendarEventSeriesService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    public ResponseEntity<CalendarEventSeriesPreviewDTO> preview(@RequestBody CalendarEventSeriesRequest request) {
        return ResponseEntity.ok(service.preview(request));
    }

    @PostMapping
    public ResponseEntity<CalendarEventSeriesDTO> create(@RequestBody CalendarEventSeriesRequest request, AbstractAuthenticationToken token) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, token));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CalendarEventSeriesDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.findOne(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CalendarEventSeriesDTO> update(
        @PathVariable Long id,
        @RequestBody CalendarEventSeriesRequest request,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.ok(service.update(id, request, token));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<CalendarEventSeriesDTO> delete(@PathVariable Long id, AbstractAuthenticationToken token) {
        return ResponseEntity.ok(service.deleteFuture(id, token));
    }

    @PostMapping("/{seriesId}/occurrences/{eventId}/restore")
    public ResponseEntity<CalendarEventSeriesDTO> restore(
        @PathVariable Long seriesId,
        @PathVariable Long eventId,
        AbstractAuthenticationToken token
    ) {
        return ResponseEntity.ok(service.restoreOccurrence(seriesId, eventId, token));
    }
}
