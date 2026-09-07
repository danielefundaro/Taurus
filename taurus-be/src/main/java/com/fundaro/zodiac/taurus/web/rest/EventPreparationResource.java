package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.*;

import com.fundaro.zodiac.taurus.service.eventpreparation.EventPreparationService;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/calendar-events/{eventId}/preparation")
@ConditionalOnProperty(prefix = "application.event-preparation", name = "enabled", havingValue = "true")
@RequiresTenantFeature(TenantFeature.EVENT_PREPARATION)
public class EventPreparationResource {
    private final EventPreparationService service;
    public EventPreparationResource(EventPreparationService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
    public ResponseEntity<View> get(@PathVariable Long eventId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(eventId));
    }

    @GetMapping("/catalogue")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ARCHIVIST')")
    public ResponseEntity<View> catalogue(@PathVariable Long eventId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getCatalogue(eventId));
    }

    @PutMapping("/configuration")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
    public View configure(@PathVariable Long eventId, @Valid @RequestBody Configuration request, AbstractAuthenticationToken token) { return service.configure(eventId, request, token); }

    @PutMapping("/program")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ARCHIVIST')")
    public View program(@PathVariable Long eventId, @Valid @RequestBody ProgramRequest request, AbstractAuthenticationToken token) { return service.replaceProgram(eventId, request, token); }

    @PutMapping("/materials")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
    @RequiresTenantFeature({TenantFeature.EVENT_PREPARATION, TenantFeature.INVENTORY})
    public View materials(@PathVariable Long eventId, @Valid @RequestBody MaterialsRequest request, AbstractAuthenticationToken token) { return service.replaceMaterials(eventId, request, token); }

    @PostMapping("/materials/{materialId}/confirm")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
    @RequiresTenantFeature({TenantFeature.EVENT_PREPARATION, TenantFeature.INVENTORY})
    public View confirmMaterial(@PathVariable Long eventId, @PathVariable Long materialId, AbstractAuthenticationToken token) { return service.confirmMaterial(eventId, materialId, token); }

    @PostMapping("/budget-confirmation")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_TREASURER')")
    @RequiresTenantFeature({TenantFeature.EVENT_PREPARATION, TenantFeature.FINANCE})
    public View confirmBudget(@PathVariable Long eventId, AbstractAuthenticationToken token) { return service.confirmBudget(eventId, token); }

    @PostMapping("/presence-confirmation")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN')")
    public View confirmPresence(@PathVariable Long eventId, AbstractAuthenticationToken token) { return service.confirmPresence(eventId, token); }

    @PostMapping("/no-movements-confirmation")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_TREASURER')")
    @RequiresTenantFeature({TenantFeature.EVENT_PREPARATION, TenantFeature.FINANCE})
    public View confirmNoMovements(@PathVariable Long eventId, AbstractAuthenticationToken token) { return service.confirmNoMovements(eventId, token); }
}
