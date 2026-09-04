package com.fundaro.zodiac.taurus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantFeatureServiceTest {

    private final TenantsRepository repository = mock(TenantsRepository.class);
    private final TenantFeatureService service = new TenantFeatureService(repository);

    @Test
    void readsOnlyTheTenantSelectedByTheContext() {
        Tenants tenantA = tenant("A", true, false);
        Tenants tenantB = tenant("B", true, true);
        when(repository.findByCodeAndDeletedFalse("A")).thenReturn(Optional.of(tenantA));
        when(repository.findByCodeAndDeletedFalse("B")).thenReturn(Optional.of(tenantB));

        TenantContext.run("A", () -> assertThat(service.isEnabled(TenantFeature.INVENTORY)).isFalse());
        TenantContext.run("B", () -> assertThat(service.isEnabled(TenantFeature.INVENTORY)).isTrue());
    }

    @Test
    void returnsTheMinimalCurrentTenantPayload() {
        Tenants tenant = tenant("ORCHESTRA_A", true, false);
        tenant.setEntityVersion(12L);
        when(repository.findByCodeAndDeletedFalse("ORCHESTRA_A")).thenReturn(Optional.of(tenant));

        TenantContext.run("ORCHESTRA_A", () -> {
            var result = service.current();
            assertThat(result.tenantCode()).isEqualTo("ORCHESTRA_A");
            assertThat(result.version()).isEqualTo(12L);
            assertThat(result.financeEnabled()).isTrue();
            assertThat(result.inventoryEnabled()).isFalse();
        });
    }

    @Test
    void deniesMissingContextsAndDisabledFeatures() {
        assertThatThrownBy(() -> service.requireEnabled(TenantFeature.FINANCE))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("tenantFeature.tenant.invalid");

        Tenants tenant = tenant("A", false, true);
        when(repository.findByCodeAndDeletedFalse("A")).thenReturn(Optional.of(tenant));
        TenantContext.run("A", () ->
            assertThatThrownBy(() -> service.requireEnabled(TenantFeature.FINANCE))
                .isInstanceOf(RequestAlertException.class)
                .extracting(error -> ((RequestAlertException) error).getErrorKey())
                .isEqualTo("tenantFeature.finance.disabled")
        );
    }

    private static Tenants tenant(String code, boolean finance, boolean inventory) {
        Tenants tenant = new Tenants();
        tenant.setCode(code);
        tenant.setActive(true);
        tenant.setDeleted(false);
        tenant.setFinanceEnabled(finance);
        tenant.setInventoryEnabled(inventory);
        return tenant;
    }
}
