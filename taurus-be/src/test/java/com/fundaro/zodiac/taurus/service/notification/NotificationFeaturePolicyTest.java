package com.fundaro.zodiac.taurus.service.notification;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationFeaturePolicyTest {

    private final NotificationFeaturePolicy policy = new NotificationFeaturePolicy();

    @Test
    void tiesTheNoticesOfTheSignatureActsToTheElectronicSignature() {
        assertThat(policy.requiredFeatures(NotificationSource.INVENTORY, "SIGNATURE_ACT", "FIRMA_RICHIESTA"))
            .containsExactlyInAnyOrder(TenantFeature.INVENTORY, TenantFeature.INVENTORY_SIGNATURE);
        assertThat(policy.requiredFeatures(NotificationSource.INVENTORY, "INVENTARIO", "OGGETTO_ASSEGNATO"))
            .containsExactly(TenantFeature.INVENTORY);
    }

    @Test
    void tiesTheMaterialNoticesOfThePreparationToTheInventory() {
        assertThat(policy.requiredFeatures(NotificationSource.CALENDAR, "EVENT_PREPARATION", "MATERIALS_UPDATED"))
            .containsExactlyInAnyOrder(TenantFeature.EVENT_PREPARATION, TenantFeature.INVENTORY);
        assertThat(policy.requiredFeatures(NotificationSource.CALENDAR, "EVENT_PREPARATION", "MATERIAL_CONFIRMATION_INVALIDATED"))
            .containsExactlyInAnyOrder(TenantFeature.EVENT_PREPARATION, TenantFeature.INVENTORY);
        assertThat(policy.requiredFeatures(NotificationSource.CALENDAR, "EVENT_PREPARATION", "PROGRAM_UPDATED"))
            .containsExactly(TenantFeature.EVENT_PREPARATION);
    }
}
