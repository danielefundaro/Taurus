package com.fundaro.zodiac.taurus.service.dto.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;

public record NotificationPreferencesDTO(
    Long version,
    @NotBlank String timeZone,
    boolean eventRemindersEnabled,
    @Min(0) @Max(1440) int defaultCalendarReminderMinutes,
    @Valid @NotNull NotificationQuietHoursDTO quietHours,
    ZonedDateTime pushPausedUntil,
    @NotNull LocalTime digestLocalTime,
    @NotNull NotificationPushPreview pushPreview,
    @Valid @NotNull List<NotificationCategoryPreferenceDTO> categories
) {}
