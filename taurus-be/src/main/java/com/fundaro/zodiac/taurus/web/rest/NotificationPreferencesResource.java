package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.service.NotificationPreferencesService;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationPreferencesDTO;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notification-preferences")
@RequiresTenantFeature(TenantFeature.NOTIFICATION_PREFERENCES)
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
