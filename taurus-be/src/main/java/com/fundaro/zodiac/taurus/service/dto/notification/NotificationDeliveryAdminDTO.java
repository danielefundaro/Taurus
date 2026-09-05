package com.fundaro.zodiac.taurus.service.dto.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationDeliveryOrigin;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import java.time.ZonedDateTime;

/**
 * Metadato tecnico esposto alla console amministrativa.
 *
 * <p>Non contiene testo editoriale, destinatario, endpoint push o stack trace:
 * soltanto identificativi, istanti, tentativi e la classe d'errore sanificata.
 */
public record NotificationDeliveryAdminDTO(
    String rowKey,
    long id,
    NotificationDeliveryOrigin origin,
    NotificationSource source,
    String operation,
    String deliveryType,
    NotificationStatus status,
    ZonedDateTime occurredAt,
    int attempts,
    ZonedDateTime updatedAt,
    ZonedDateTime nextAttemptAt,
    String errorClass,
    String skipReason,
    String eventKeyHash
) {
    public static String rowKey(NotificationDeliveryOrigin origin, long id) {
        return origin.name() + ":" + id;
    }
}
