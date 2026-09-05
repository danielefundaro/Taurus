package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
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
    private final ZoneId defaultZoneId;

    public TenantTimeZoneService(TenantsRepository tenantsRepository, ApplicationProperties applicationProperties) {
        this.tenantsRepository = tenantsRepository;
        this.defaultZoneId = ZoneId.of(applicationProperties.getNotificationPreferences().getDefaultTimeZone());
    }

    @Transactional(readOnly = true)
    public ZoneId currentZoneId() {
        String tenantCode = TenantContext.getTenantCode().orElseThrow(() ->
            new RequestAlertException(HttpStatus.BAD_REQUEST, "Tenant context is required", "CalendarEventSeries", "tenant.missing")
        );
        String timeZone = tenantsRepository.findByCodeAndDeletedFalse(tenantCode)
            .map(tenant -> tenant.getTimeZone())
            .orElse(null);
        if (timeZone == null || timeZone.isBlank()) return defaultZoneId;
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException exception) {
            return defaultZoneId;
        }
    }
}
