package com.fundaro.zodiac.taurus.domain.notification;

public enum NotificationAudienceType {
    ROLE,
    USER,
    ALL_ACTIVE_USERS,
    /** Removes a user from the recipients resolved by the other audiences, typically the author of the action. */
    EXCLUDED_USER
}
