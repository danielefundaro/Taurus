package com.fundaro.zodiac.taurus.service.dto.notification;

import jakarta.validation.constraints.NotNull;
import java.time.ZonedDateTime;

public record SnoozeNoticeRequest(@NotNull ZonedDateTime until) {}
