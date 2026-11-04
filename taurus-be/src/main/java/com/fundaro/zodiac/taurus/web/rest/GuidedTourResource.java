package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourDTO;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourProgressRequest;
import com.fundaro.zodiac.taurus.service.dto.guidedtour.GuidedTourSettingsRequest;
import com.fundaro.zodiac.taurus.service.guidedtour.GuidedTourService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/**
 * The guided tour of the session. Person, role and tenant come from the token: no endpoint accepts them.
 */
@RestController
@RequestMapping("/api/guided-tour")
public class GuidedTourResource {

    private final GuidedTourService service;

    public GuidedTourResource(GuidedTourService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<GuidedTourDTO> get(AbstractAuthenticationToken authentication) {
        return ResponseEntity.ok(service.get(authentication));
    }

    @PutMapping("/settings")
    public ResponseEntity<GuidedTourDTO> saveSettings(
        @Valid @RequestBody GuidedTourSettingsRequest settings,
        AbstractAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.saveSettings(settings, authentication));
    }

    @PutMapping("/progress")
    public ResponseEntity<GuidedTourDTO> saveProgress(
        @RequestBody GuidedTourProgressRequest progress,
        AbstractAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(service.saveProgress(progress, authentication));
    }

    @DeleteMapping("/progress")
    public ResponseEntity<GuidedTourDTO> deleteProgress(AbstractAuthenticationToken authentication) {
        return ResponseEntity.ok(service.deleteProgress(authentication));
    }
}
