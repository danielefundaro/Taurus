package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.impl.TenantLogoLoader;
import com.fundaro.zodiac.taurus.service.loansheet.LoanSheetContent.Tenant;

import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;

import static com.fundaro.zodiac.taurus.service.loansheet.LoanSheetSupport.tenant;

/**
 * Data of the current tenant printed on the loan sheet.
 */
@Component
public class LoanSheetTenantData {

    private final TenantsService tenantsService;
    private final TenantLogoLoader tenantLogoLoader;

    public LoanSheetTenantData(TenantsService tenantsService, TenantLogoLoader tenantLogoLoader) {
        this.tenantsService = tenantsService;
        this.tenantLogoLoader = tenantLogoLoader;
    }

    public Optional<TenantsDTO> dto(AbstractAuthenticationToken token) {
        return tenantsService.findByCode(tenant(token), token);
    }

    /**
     * Returns the tenant data for a template; the logo is downloaded only when the template prints it.
     */
    public Tenant forTemplate(String template, AbstractAuthenticationToken token) {
        return forTemplate(template, tenant(token));
    }

    /**
     * Returns the data of the tenant with the given code, also for a request without authentication.
     */
    public Tenant forTemplate(String template, String code) {
        TenantsDTO tenant = tenantsService.findByCode(code, null).orElse(null);
        if (tenant == null) return new Tenant(code, null, null, null, null, null, null, null, null, null, null);
        boolean withLogo = template != null && template.contains(LoanSheetVariables.LOGO);
        String logo = withLogo
            ? tenantLogoLoader.load(tenant.getLogoUrl()).map(bytes -> "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes)).orElse(null)
            : null;
        return new Tenant(
            Objects.requireNonNullElse(tenant.getName(), code),
            tenant.getAddress(),
            tenant.getPostalCode(),
            tenant.getCity(),
            tenant.getProvince(),
            tenant.getEmail(),
            tenant.getPhone(),
            tenant.getPec(),
            tenant.getVatNumber(),
            tenant.getTaxCode(),
            logo
        );
    }
}
