package com.fundaro.zodiac.taurus.service.dto.notification;

import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record NotificationQuietHoursDTO(boolean enabled, @NotNull LocalTime start, @NotNull LocalTime end) {}
