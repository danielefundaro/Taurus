package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.notification.NotificationAudienceType;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutboxAudience;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import com.fundaro.zodiac.taurus.service.notification.NotificationEventKey;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationOutboxPublisher {

    private static final Pattern MARKUP = Pattern.compile("<[^>]*>");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");
    private final NotificationOutboxRepository repository;

    public NotificationOutboxPublisher(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(NotificationCommand rawCommand) {
        NotificationCommand command = normalizeAndValidate(rawCommand);
        if (repository.existsByEventKey(command.eventKey())) return;

        ZonedDateTime now = ZonedDateTime.now();
        NotificationOutbox event = new NotificationOutbox();
        event.initializeAudit(command.actorId());
        event.setEventKey(command.eventKey());
        event.setSource(command.source());
        event.setAggregateType(command.aggregateType());
        event.setAggregateId(command.aggregateId());
        event.setOperation(command.operation());
        event.setTitle(command.title());
        event.setMessage(command.message());
        event.setSeverity(command.severity());
        event.setPreferencePolicy(command.preferencePolicy());
        event.setTargetPath(command.targetPath());
        event.setActorId(command.actorId());
        event.setActorDisplayName(command.actorDisplayName());
        event.setOccurredAt(now);
        event.setStatus(NotificationStatus.PENDING);
        event.setAttempts(0);
        event.setNextAttemptAt(now);
        command.audiences().forEach(value -> {
            NotificationOutboxAudience audience = new NotificationOutboxAudience();
            audience.setType(value.type());
            audience.setValue(value.value());
            event.addAudience(audience);
        });
        repository.save(event);
    }

    NotificationCommand normalizeAndValidate(NotificationCommand command) {
        if (command == null) throw new IllegalArgumentException("Notification command is required");
        String eventKey = NotificationEventKey.fit(required(command.eventKey(), "eventKey"));
        String aggregateType = required(command.aggregateType(), "aggregateType", 64).toUpperCase(Locale.ROOT);
        String operation = required(command.operation(), "operation", 64).toUpperCase(Locale.ROOT);
        String title = plainText(command.title(), "title");
        String message = plainText(command.message(), "message");
        String actorId = required(command.actorId(), "actorId", 255);
        String actorDisplayName = required(command.actorDisplayName(), "actorDisplayName", 255);
        if (command.source() == null) throw new IllegalArgumentException("source is required");
        NotificationSeverity severity = command.severity() == null ? NotificationSeverity.INFO : command.severity();
        NotificationPreferencePolicy preferencePolicy = command.preferencePolicy() == null
            ? NotificationPreferencePolicy.CONFIGURABLE
            : command.preferencePolicy();
        String targetPath = trimToNull(command.targetPath());
        if (targetPath != null && (!targetPath.startsWith("/") || targetPath.contains("://") || targetPath.toLowerCase(Locale.ROOT).contains("javascript:"))) {
            throw new IllegalArgumentException("targetPath must be an internal application path");
        }
        if (targetPath != null && targetPath.length() > 500) throw new IllegalArgumentException("targetPath exceeds 500 characters");

        if (command.audiences() == null || command.audiences().isEmpty()) {
            throw new IllegalArgumentException("At least one notification audience is required");
        }
        Set<NotificationAudience> audiences = new LinkedHashSet<>();
        for (NotificationAudience rawAudience : command.audiences()) {
            if (rawAudience == null || rawAudience.type() == null) throw new IllegalArgumentException("Audience type is required");
            String value = required(rawAudience.value(), "audience value", 255);
            if (rawAudience.type() == NotificationAudienceType.ROLE) {
                RoleEnum.valueOf(value);
            } else if (rawAudience.type() == NotificationAudienceType.ALL_ACTIVE_USERS && !"*".equals(value)) {
                throw new IllegalArgumentException("ALL_ACTIVE_USERS audience must use '*'");
            }
            audiences.add(new NotificationAudience(rawAudience.type(), value));
        }
        return new NotificationCommand(
            eventKey,
            command.source(),
            aggregateType,
            trimToNull(command.aggregateId()),
            operation,
            title,
            message,
            severity,
            preferencePolicy,
            targetPath,
            actorId,
            actorDisplayName,
            Set.copyOf(audiences),
            trimToNull(command.targetTenantCode())
        );
    }

    private static String plainText(String value, String field) {
        String normalized = required(value, field, 255);
        if (MARKUP.matcher(normalized).find() || CONTROL.matcher(normalized).find()) {
            throw new IllegalArgumentException(field + " must be plain text");
        }
        return normalized;
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = required(value, field);
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        return normalized;
    }

    private static String required(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
