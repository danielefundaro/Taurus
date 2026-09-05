package com.fundaro.zodiac.taurus.service.dashboard;

import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.repository.notification.NotificationDeliveryAdminQueryRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationDeliveryAdminQueryRepository.NotificationDeliverySummary;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardDomain;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardOperationType;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardSeverity;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Il totale mostrato dalla dashboard aggrega le tre origini tecniche: fan-out
 * in-app, consegne push e promemoria evento. Il dettaglio per origine resta
 * esclusivo della console amministrativa.
 */
@Service
public class NotificationOperationProvider implements DashboardOperationProvider {

    private final NotificationDeliveryAdminQueryRepository queryRepository;

    public NotificationOperationProvider(NotificationDeliveryAdminQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Override
    public DashboardDomain domain() {
        return DashboardDomain.NOTIFICATIONS;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<OperationalItemDTO> getOperations(DashboardRequestContext context) {
        if (!context.hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN)) return List.of();
        NotificationDeliverySummary summary = queryRepository.summarize(NotificationStatus.FAILED);
        if (summary == null || summary.failureCount() == 0) return List.of();
        return List.of(new OperationalItemDTO(
            DashboardOperationType.NOTIFICATION_DELIVERY_FAILED.name(),
            DashboardOperationType.NOTIFICATION_DELIVERY_FAILED,
            DashboardDomain.NOTIFICATIONS,
            DashboardSeverity.DANGER,
            summary.failureCount(),
            null,
            "Consegne tecniche fallite",
            "Eventi tecnici da riprocessare; contenuti e destinatari non sono mostrati.",
            summary.oldestOccurredAt(),
            "Apri console",
            "/admin/notification-delivery?status=FAILED"
        ));
    }
}
