package com.fundaro.zodiac.taurus.service.notification;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class NotificationTiming {

    private NotificationTiming() {}

    public static ZonedDateTime nextAllowed(NotificationPreferenceDecision preference, ZonedDateTime requested) {
        ZonedDateTime candidate = requested;
        if (preference.pushPausedUntil() != null && preference.pushPausedUntil().isAfter(candidate)) {
            candidate = preference.pushPausedUntil();
        }
        if (!preference.quietHoursEnabled()) return candidate;
        ZonedDateTime local = candidate.withZoneSameInstant(preference.timeZone());
        LocalTime time = local.toLocalTime();
        if (!isWithin(preference.quietStart(), preference.quietEnd(), time)) return candidate;
        LocalDate endDate;
        if (preference.quietStart().isBefore(preference.quietEnd())) {
            endDate = local.toLocalDate();
        } else {
            endDate = time.isBefore(preference.quietEnd()) ? local.toLocalDate() : local.toLocalDate().plusDays(1);
        }
        return ZonedDateTime.of(endDate, preference.quietEnd(), preference.timeZone());
    }

    public static ZonedDateTime nextDigest(NotificationPreferenceDecision preference, ZonedDateTime now) {
        ZonedDateTime localNow = now.withZoneSameInstant(preference.timeZone());
        LocalDate date = localNow.toLocalTime().isBefore(preference.digestLocalTime())
            ? localNow.toLocalDate()
            : localNow.toLocalDate().plusDays(1);
        return ZonedDateTime.of(date, preference.digestLocalTime(), preference.timeZone());
    }

    public static boolean isWithin(LocalTime start, LocalTime end, LocalTime value) {
        if (start.isBefore(end)) return !value.isBefore(start) && value.isBefore(end);
        return !value.isBefore(start) || value.isBefore(end);
    }
}
