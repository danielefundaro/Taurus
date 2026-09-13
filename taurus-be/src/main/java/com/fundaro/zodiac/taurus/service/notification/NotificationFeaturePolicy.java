package com.fundaro.zodiac.taurus.service.notification;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class NotificationFeaturePolicy {
    public Set<TenantFeature> requiredFeatures(NotificationSource source, String aggregateType, String operation) {
        Set<TenantFeature> result = new LinkedHashSet<>();
        if (source == NotificationSource.FINANCE) result.add(TenantFeature.FINANCE);
        if (source == NotificationSource.INVENTORY) result.add(TenantFeature.INVENTORY);
        if ("INVENTORY_ISSUE".equals(aggregateType)) result.add(TenantFeature.INVENTORY_QR);
        if ("EVENT_PREPARATION".equals(aggregateType)) {
            result.add(TenantFeature.EVENT_PREPARATION);
            if ("FINANCE_FOLLOW_UP".equals(operation)) result.add(TenantFeature.FINANCE);
            if ("MATERIALS_UPDATED".equals(operation)) result.add(TenantFeature.INVENTORY);
        }
        return Set.copyOf(result);
    }
}
