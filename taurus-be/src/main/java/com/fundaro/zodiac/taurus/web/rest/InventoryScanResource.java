package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.impl.InventoryScanRateLimiter;
import com.fundaro.zodiac.taurus.service.impl.InventoryScanService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory-scan/v1")
public class InventoryScanResource {
    private final InventoryScanService scanService;
    private final InventoryScanRateLimiter rateLimiter;
    private final TenantFeatureService tenantFeatures;

    public InventoryScanResource(InventoryScanService scanService, InventoryScanRateLimiter rateLimiter, TenantFeatureService tenantFeatures) {
        this.scanService = scanService;
        this.rateLimiter = rateLimiter;
        this.tenantFeatures = tenantFeatures;
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<?> resolve(
        @PathVariable String publicId,
        AbstractAuthenticationToken token,
        HttpServletRequest request
    ) {
        if (!tenantFeatures.isEnabled(TenantFeature.INVENTORY_QR)) return ResponseEntity.notFound().build();
        if (!rateLimiter.tryAcquire(SecurityUtils.getUserIdFromAuthentication(token), request.getRemoteAddr())) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Troppe scansioni. Riprova tra un minuto.");
            problem.setTitle("Limite scansioni superato");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .cacheControl(CacheControl.noStore())
                .body(problem);
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("Referrer-Policy", "no-referrer")
            .body(scanService.resolve(publicId, token));
    }
}
