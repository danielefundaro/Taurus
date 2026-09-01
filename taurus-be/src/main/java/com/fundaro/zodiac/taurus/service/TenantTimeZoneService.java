package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.time.DateTimeException;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantTimeZoneService {

    private final TenantsRepository tenantsRepository;

    public TenantTimeZoneService(TenantsRepository tenantsRepository) {
        this.tenantsRepository = tenantsRepository;
    }

    @Transactional(readOnly = true)
    public ZoneId currentZoneId() {
        String tenantCode = TenantContext.getTenantCode().orElseThrow(() ->
            new RequestAlertException(HttpStatus.BAD_REQUEST, "Tenant context is required", "CalendarEventSeries", "tenant.missing")
        );
        String timeZone = tenantsRepository.findByCodeAndDeletedFalse(tenantCode)
            .map(tenant -> tenant.getTimeZone())
            .orElseThrow(() -> new RequestAlertException(HttpStatus.NOT_FOUND, "Tenant not found", "CalendarEventSeries", "tenant.notFound"));
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException exception) {
            throw new RequestAlertException(HttpStatus.BAD_REQUEST, "Invalid tenant time zone", "CalendarEventSeries", "timeZone.invalid");
        }
    }
}
