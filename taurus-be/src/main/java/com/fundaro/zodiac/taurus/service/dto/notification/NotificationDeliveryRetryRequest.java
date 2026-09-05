package com.fundaro.zodiac.taurus.service.dto.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NotificationDeliveryRetryRequest(
    @NotEmpty @Size(max = 100) List<@NotNull @Valid NotificationDeliveryRef> refs
) {}
