package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.OperationalDashboardService;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalDashboardDTO;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class OperationalDashboardResource {

    private final OperationalDashboardService service;

    public OperationalDashboardResource(OperationalDashboardService service) {
        this.service = service;
    }

    @GetMapping("/operations")
    public ResponseEntity<OperationalDashboardDTO> getOperations(AbstractAuthenticationToken authentication) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(service.getOperations(authentication));
    }
}
