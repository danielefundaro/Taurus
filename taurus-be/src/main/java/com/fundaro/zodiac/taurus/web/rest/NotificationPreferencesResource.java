package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.NotificationPreferencesService;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationPreferencesDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification-preferences")
public class NotificationPreferencesResource {

    private final NotificationPreferencesService service;

    public NotificationPreferencesResource(NotificationPreferencesService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<NotificationPreferencesDTO> get(AbstractAuthenticationToken authentication) {
        return ResponseEntity.ok(service.get(authentication));
    }

    @PutMapping
    public ResponseEntity<NotificationPreferencesDTO> save(
        @Valid @RequestBody NotificationPreferencesDTO preferences,
        AbstractAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.save(preferences, authentication));
    }
}
