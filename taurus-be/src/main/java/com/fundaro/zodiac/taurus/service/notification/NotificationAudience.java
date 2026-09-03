package com.fundaro.zodiac.taurus.service.notification;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.notification.NotificationAudienceType;

public record NotificationAudience(NotificationAudienceType type, String value) {
    public static NotificationAudience role(RoleEnum role) {
        return new NotificationAudience(NotificationAudienceType.ROLE, role.name());
    }

    public static NotificationAudience user(String userId) {
        return new NotificationAudience(NotificationAudienceType.USER, userId);
    }

    public static NotificationAudience allActiveUsers() {
        return new NotificationAudience(NotificationAudienceType.ALL_ACTIVE_USERS, "*");
    }
}
