package com.fundaro.zodiac.taurus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TenantFeatureServiceTest {

    private final TenantsRepository repository = mock(TenantsRepository.class);
    private final ApplicationProperties properties = new ApplicationProperties();
    private final TenantFeatureService service = new TenantFeatureService(repository, properties);

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
        tenant.setName("Orchestra Aurora");
        tenant.setEntityVersion(12L);
        when(repository.findByCodeAndDeletedFalse("ORCHESTRA_A")).thenReturn(Optional.of(tenant));

        TenantContext.run("ORCHESTRA_A", () -> {
            var result = service.current();
            assertThat(result.tenantCode()).isEqualTo("ORCHESTRA_A");
            assertThat(result.tenantName()).isEqualTo("Orchestra Aurora");
            assertThat(result.version()).isEqualTo(12L);
            assertThat(result.financeEnabled()).isTrue();
            assertThat(result.inventoryEnabled()).isFalse();
            assertThat(result.eventPreparationEnabled()).isFalse();
            assertThat(result.inventoryQrEnabled()).isFalse();
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

    @Test
    void appliesApplicationCapabilitiesAndFeatureDependencies() {
        Tenants tenant = tenant("A", true, true);
        tenant.setOnboardingImportEnabled(true);
        tenant.setExternalCalendarFeedEnabled(true);
        tenant.setInventoryQrEnabled(true);
        tenant.setNotificationPreferencesEnabled(true);
        tenant.setWebPushRemindersEnabled(true);
        tenant.setEventPreparationEnabled(true);
        when(repository.findByCodeAndDeletedFalse("A")).thenReturn(Optional.of(tenant));

        properties.getInventory().getQr().setEnabled(true);
        properties.getInventory().getQr().setPublicBaseUrl("https://taurus.example");
        properties.getVapid().setPublicKey("public-key");
        properties.getVapid().setPrivateKey("private-key");
        properties.getEventPreparation().setEnabled(true);

        TenantContext.run("A", () -> {
            assertThat(service.current())
                .returns(true, result -> result.onboardingImportEnabled())
                .returns(true, result -> result.externalCalendarFeedEnabled())
                .returns(true, result -> result.inventoryQrEnabled())
                .returns(true, result -> result.notificationPreferencesEnabled())
                .returns(true, result -> result.webPushRemindersEnabled())
                .returns(true, result -> result.eventPreparationEnabled());

            tenant.setInventoryEnabled(false);
            assertThat(service.isEnabled(TenantFeature.INVENTORY_QR)).isFalse();
            tenant.setNotificationPreferencesEnabled(false);
            assertThat(service.isEnabled(TenantFeature.WEB_PUSH_REMINDERS)).isFalse();
        });

        assertThat(service.capabilities().get(TenantFeature.INVENTORY_QR).dependencies())
            .containsExactly(TenantFeature.INVENTORY);
        assertThat(service.capabilities().get(TenantFeature.WEB_PUSH_REMINDERS).dependencies())
            .containsExactly(TenantFeature.NOTIFICATION_PREFERENCES);
    }

    @Test
    void defaultsEveryTenantFeatureToDisabled() {
        Tenants tenant = new Tenants();
        assertThat(tenant.getFinanceEnabled()).isFalse();
        assertThat(tenant.getInventoryEnabled()).isFalse();
        assertThat(tenant.getOnboardingImportEnabled()).isFalse();
        assertThat(tenant.getExternalCalendarFeedEnabled()).isFalse();
        assertThat(tenant.getInventoryQrEnabled()).isFalse();
        assertThat(tenant.getNotificationPreferencesEnabled()).isFalse();
        assertThat(tenant.getWebPushRemindersEnabled()).isFalse();
        assertThat(tenant.getEventPreparationEnabled()).isFalse();
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
