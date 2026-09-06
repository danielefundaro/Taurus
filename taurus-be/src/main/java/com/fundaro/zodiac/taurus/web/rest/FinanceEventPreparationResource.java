package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.View;
import com.fundaro.zodiac.taurus.service.eventpreparation.EventPreparationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/events/{eventId}")
@RequiresTenantFeature(TenantFeature.FINANCE)
@ConditionalOnProperty(prefix = "application.event-preparation", name = "enabled", havingValue = "true")
public class FinanceEventPreparationResource {
    private final EventPreparationService service;

    public FinanceEventPreparationResource(EventPreparationService service) {
        this.service = service;
    }

    @GetMapping("/preparation")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_TREASURER')")
    public ResponseEntity<View> get(@PathVariable Long eventId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.getFinance(eventId));
    }

    @PostMapping("/budget-confirmation")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_TREASURER')")
    public View confirmBudget(@PathVariable Long eventId, AbstractAuthenticationToken token) {
        return service.confirmBudget(eventId, token);
    }

    @PostMapping("/no-movements-confirmation")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_TREASURER')")
    public View confirmNoMovements(@PathVariable Long eventId, AbstractAuthenticationToken token) {
        return service.confirmNoMovements(eventId, token);
    }
}
