package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.View;
import com.fundaro.zodiac.taurus.service.eventpreparation.EventPreparationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/calendar-events/{eventId}/preparation")
@ConditionalOnProperty(prefix = "application.event-preparation", name = "enabled", havingValue = "true")
public class UserEventPreparationResource {
    private final EventPreparationService service;

    public UserEventPreparationResource(EventPreparationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<View> get(@PathVariable Long eventId, AbstractAuthenticationToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getPersonal(eventId, token, false));
    }
}
