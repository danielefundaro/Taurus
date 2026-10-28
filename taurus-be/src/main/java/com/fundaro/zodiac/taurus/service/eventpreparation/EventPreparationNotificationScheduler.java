package com.fundaro.zodiac.taurus.service.eventpreparation;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.service.eventpreparation.EventPreparationService.DashboardEntry;
import com.fundaro.zodiac.taurus.service.impl.NotificationOutboxPublisher;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "application.event-preparation", name = "enabled", havingValue = "true")
public class EventPreparationNotificationScheduler {

    static final String SYSTEM_ACTOR = "event-preparation-scheduler";
    private static final Logger log = LoggerFactory.getLogger(EventPreparationNotificationScheduler.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Rome");
    private static final Set<NotificationAudience> ADMIN_AUDIENCE = Set.of(
        NotificationAudience.role(RoleEnum.ROLE_ADMIN),
        NotificationAudience.role(RoleEnum.ROLE_SUPER_ADMIN)
    );
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;
    private final EventPreparationService preparationService;
    private final NotificationOutboxPublisher notificationPublisher;
    private EventPreparationMetrics metrics;

    public EventPreparationNotificationScheduler(
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor,
        EventPreparationService preparationService,
        NotificationOutboxPublisher notificationPublisher
    ) {
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
        this.preparationService = preparationService;
        this.notificationPublisher = notificationPublisher;
    }

    @Autowired(required = false)
    void setMetrics(EventPreparationMetrics metrics) {
        this.metrics = metrics;
    }

    @Scheduled(
        cron = "${application.event-preparation.notification-cron:0 * * * * *}",
        zone = "${application.event-preparation.notification-zone:Europe/Rome}"
    )
    public void notifyDeadlines() {
        ZonedDateTime now = ZonedDateTime.now(DEFAULT_ZONE);
        tenantSchemaRegistry.findEventPreparationEnabledTenantCodes().forEach(tenantCode -> {
            try {
                tenantTransactionExecutor.execute(tenantCode, () -> notifyCurrentTenant(now));
            } catch (RuntimeException exception) {
                log.error("Unable to process event preparation notifications for tenant {}", tenantCode, exception);
            }
        });
    }

    void notifyCurrentTenant(ZonedDateTime now) {
        preparationService.scheduledEntries(now.minusDays(30), now.plusDays(31)).forEach(entry -> {
            if (entry.availabilityDeadline() != null && !now.isBefore(entry.availabilityDeadline()) && entry.issueCodes().contains("AVAILABILITY_INCOMPLETE")) {
                enqueue(entry, "AVAILABILITY_THRESHOLD_MISSED", entry.availabilityDeadline().toInstant().toString(), "Disponibilità insufficienti", "La soglia di disponibilità per “" + entry.eventName() + "” non è stata raggiunta.", ADMIN_AUDIENCE);
            }
            if (!now.isBefore(entry.endedAt()) && entry.issueCodes().contains("PRESENCE_NOT_CONFIRMED")) {
                enqueue(entry, "PRESENCE_FOLLOW_UP", entry.endedAt().toInstant().toString(), "Presenze da confermare", "Verifica le presenze di “" + entry.eventName() + "”.", ADMIN_AUDIENCE);
            }
            if (!now.isBefore(entry.endedAt()) && entry.issueCodes().contains("FINANCE_NOT_CLOSED")) {
                Set<NotificationAudience> financeAudience = new LinkedHashSet<>(ADMIN_AUDIENCE);
                financeAudience.add(NotificationAudience.role(RoleEnum.ROLE_TREASURER));
                enqueue(entry, "FINANCE_FOLLOW_UP", entry.endedAt().toInstant().toString(), "Chiusura economica richiesta", "Completa la chiusura economica di “" + entry.eventName() + "”.", Set.copyOf(financeAudience));
            }
            // A confirmation is notified once per obsolete hash: confirming again and changing again notifies again.
            if (now.isBefore(entry.endedAt()))
                entry.obsoleteMaterialConfirmations().forEach(obsolete -> {
                    boolean recorded = enqueue(entry, "MATERIAL_CONFIRMATION_INVALIDATED", obsolete.materialId() + ":" + obsolete.confirmationHash(),
                        "Conferma materiale non più valida",
                        "La preparazione di “" + obsolete.itemName() + "” per “" + entry.eventName() + "” è cambiata dopo la conferma: verificala di nuovo.", ADMIN_AUDIENCE);
                    if (recorded && metrics != null) metrics.confirmationInvalidated("material");
                });
        });
    }

    private boolean enqueue(
        DashboardEntry entry,
        String operation,
        String revision,
        String title,
        String message,
        Set<NotificationAudience> audiences
    ) {
        return notificationPublisher.enqueue(new NotificationCommand(
            "event-preparation:" + entry.eventId() + ":" + operation + ":" + revision,
            NotificationSource.CALENDAR,
            "EVENT_PREPARATION",
            entry.eventId().toString(),
            operation,
            title,
            message,
            NotificationSeverity.WARNING,
            NotificationPreferencePolicy.CONFIGURABLE,
            "/calendar/" + entry.eventId() + (operation.startsWith("MATERIAL") ? "#preparation-materials" : "#preparation"),
            SYSTEM_ACTOR,
            "Taurus",
            audiences,
            null
        ));
    }
}
