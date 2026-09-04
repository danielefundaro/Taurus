package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.notification.NotificationDelivery;
import com.fundaro.zodiac.taurus.service.notification.NotificationEventKey;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDispatcher {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(NotificationDispatcher.class);
    static final String ACTOR = "notification-dispatcher";
    private final NotificationOutboxRepository repository;
    private final NotificationRecipientResolver recipientResolver;
    private final NoticesService noticesService;
    private final ApplicationProperties.NotificationProperties properties;
    private final NotificationMetrics metrics;
    private final TenantFeatureService tenantFeatureService;

    public NotificationDispatcher(
        NotificationOutboxRepository repository,
        NotificationRecipientResolver recipientResolver,
        NoticesService noticesService,
        ApplicationProperties applicationProperties,
        NotificationMetrics metrics,
        TenantFeatureService tenantFeatureService
    ) {
        this.repository = repository;
        this.recipientResolver = recipientResolver;
        this.noticesService = noticesService;
        this.properties = applicationProperties.getNotifications();
        this.metrics = metrics;
        this.tenantFeatureService = tenantFeatureService;
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<Long> findReadyIds() {
        ZonedDateTime now = ZonedDateTime.now();
        metrics.recordPending(repository.summarizeByStatus(NotificationStatus.PENDING), now);
        return repository.findReadyIds(
            NotificationStatus.PENDING,
            now,
            PageRequest.of(0, Math.max(1, properties.getBatchSize()))
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void dispatch(long id) {
        NotificationOutbox event = repository.findByIdForUpdate(id).orElse(null);
        ZonedDateTime now = ZonedDateTime.now();
        if (event == null || event.getStatus() != NotificationStatus.PENDING || event.getNextAttemptAt().isAfter(now)) return;
        if (!sourceEnabled(event.getSource())) {
            event.setStatus(NotificationStatus.SUPPRESSED);
            event.setLastError(null);
            event.touchAudit(ACTOR);
            repository.save(event);
            LOG.info("notification_suppressed tenant={} eventId={} source={} reason=tenant_feature_disabled", tenant(), event.getId(), event.getSource());
            return;
        }
        metrics.recordAttempt(event);
        Set<String> recipients = recipientResolver.resolve(event.getAudiences());
        if (recipients.isEmpty()) throw new IllegalStateException("No active notification recipients are available");
        recipients.forEach(userId -> noticesService.addNoticeToUser(new NotificationDelivery(
            userId,
            event.getEventKey(),
            event.getTitle(),
            event.getMessage(),
            event.getSource(),
            event.getSeverity(),
            event.getTargetPath(),
            event.getActorId()
        )));
        event.setStatus(NotificationStatus.DELIVERED);
        event.setDeliveredAt(now);
        event.setLastError(null);
        event.touchAudit(ACTOR);
        repository.save(event);
        metrics.recordDelivered(event, recipients.size(), now);
        LOG.info(
            "notification_delivered tenant={} eventId={} eventKeyHash={} source={} operation={} attempt={} recipients={} deliveredAt={}",
            tenant(), event.getId(), NotificationEventKey.hashForLog(event.getEventKey()), event.getSource(), event.getOperation(),
            event.getAttempts() + 1, recipients.size(), now
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markFailure(long id, RuntimeException exception) {
        NotificationOutbox event = repository.findByIdForUpdate(id).orElse(null);
        if (event == null || event.getStatus() != NotificationStatus.PENDING) return;
        int attempts = event.getAttempts() + 1;
        int maxAttempts = Math.max(0, properties.getRetry().getMaxAttempts());
        event.setAttempts(attempts);
        event.setLastError(sanitizedError(exception));
        if (maxAttempts > 0 && attempts >= maxAttempts) {
            event.setStatus(NotificationStatus.FAILED);
        } else {
            event.setNextAttemptAt(ZonedDateTime.now().plus(retryDelay(attempts)));
        }
        event.touchAudit(ACTOR);
        repository.save(event);
        metrics.recordFailure(event);
        LOG.warn(
            "notification_delivery_failed tenant={} eventId={} eventKeyHash={} source={} operation={} attempt={} nextAttemptAt={} status={} errorClass={}",
            tenant(), event.getId(), NotificationEventKey.hashForLog(event.getEventKey()), event.getSource(), event.getOperation(),
            attempts, event.getNextAttemptAt(), event.getStatus(), exception.getClass().getName()
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long deleteDeliveredBefore(ZonedDateTime cutoff) {
        return repository.deleteAllByStatusAndDeliveredAtBefore(NotificationStatus.DELIVERED, cutoff);
    }

    Duration retryDelay(int attempts) {
        long initial = Math.max(1, properties.getRetry().getInitialDelayMinutes());
        long maximum = Math.max(initial, properties.getRetry().getMaxDelayMinutes());
        long factor = 1L << Math.min(Math.max(0, attempts - 1), 20);
        return Duration.ofMinutes(Math.min(maximum, initial * factor));
    }

    static String sanitizedError(RuntimeException exception) {
        String rawMessage = exception.getMessage();
        String message = exception.getClass().getSimpleName() + (rawMessage == null ? "" : ": " + rawMessage);
        message = message.replaceAll("[\\r\\n\\t]+", " ");
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private static String tenant() {
        return com.fundaro.zodiac.taurus.multitenancy.TenantContext.getTenantCode().orElse("unknown");
    }

    private boolean sourceEnabled(NotificationSource source) {
        if (source == NotificationSource.FINANCE) return tenantFeatureService.isEnabled(TenantFeature.FINANCE);
        if (source == NotificationSource.INVENTORY) return tenantFeatureService.isEnabled(TenantFeature.INVENTORY);
        return true;
    }
}
