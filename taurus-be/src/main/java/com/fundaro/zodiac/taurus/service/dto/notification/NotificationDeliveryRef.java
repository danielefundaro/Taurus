package com.fundaro.zodiac.taurus.service.dto.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationDeliveryOrigin;
import jakarta.validation.constraints.NotNull;

/** Riferimento a una riga tecnica: l'ID da solo non è univoco tra le tre origini. */
public record NotificationDeliveryRef(
    @NotNull NotificationDeliveryOrigin origin,
    @NotNull Long id
) {}
