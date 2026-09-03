package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.utils.pdf.PdfPageWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class TenantPdfHeaderService {

    private static final Logger LOG = LoggerFactory.getLogger(TenantPdfHeaderService.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TenantsService tenantsService;
    private final TenantLogoLoader tenantLogoLoader;

    public TenantPdfHeaderService(TenantsService tenantsService, TenantLogoLoader tenantLogoLoader) {
        this.tenantsService = tenantsService;
        this.tenantLogoLoader = tenantLogoLoader;
    }

    public void write(
        PdfPageWriter writer,
        String reportTitle,
        String tenantCode,
        ZonedDateTime generatedAt,
        AbstractAuthenticationToken token
    ) throws IOException {
        TenantsDTO tenant = tenantsService.findByCode(tenantCode, token).orElse(null);
        includeLogo(writer, tenant, tenantCode);
        writer.title(reportTitle);
        writer.line(tenant == null ? safe(tenantCode) : safe(tenant.getName()), true);
        if (tenant != null) {
            writer.line("Sede: " + safe(joinAddress(tenant)), false);
            writer.line("Codice fiscale: " + safe(tenant.getTaxCode()) + " - Partita IVA: " + safe(tenant.getVatNumber()), false);
        }
        writer.line("Generato il: " + DATE_TIME.format(generatedAt), false);
    }

    private void includeLogo(PdfPageWriter writer, TenantsDTO tenant, String tenantCode) {
        if (tenant == null || tenant.getLogoUrl() == null || tenant.getLogoUrl().isBlank()) return;
        boolean included = tenantLogoLoader.load(tenant.getLogoUrl()).map(writer::headerLogo).orElse(false);
        if (!included) LOG.warn("Tenant logo could not be included in PDF report for tenant {}", tenantCode);
    }

    private static String joinAddress(TenantsDTO tenant) {
        return Stream.of(tenant.getAddress(), tenant.getPostalCode(), tenant.getCity(), tenant.getProvince(), tenant.getCountry())
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.joining(", "));
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
