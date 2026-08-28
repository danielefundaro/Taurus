package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.criteria.TenantsCriteria;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link Tenants}.
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantsResource extends CommonOpenSearchResource<Tenants, TenantsDTO, TenantsCriteria, TenantsService> {

    public TenantsResource(TenantsService tenantsService) {
        super(tenantsService, Tenants.class.getSimpleName(), TenantsResource.class);
    }

    /**
     * {@code DELETE /{id}/gdpr} : Permanently erase a tenant and all associated data.
     */
    @DeleteMapping("/{id}/gdpr")
    public ResponseEntity<Void> deleteEntityForGdpr(@PathVariable("id") Long id,
                                                     AbstractAuthenticationToken abstractAuthenticationToken) {
        getLog().info("REST request to permanently erase tenant {} under GDPR", id);
        getService().deleteForGdpr(id, abstractAuthenticationToken);
        return ResponseEntity.noContent().build();
    }
}
